package com.example.admin.sqltrace.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.admin.sqltrace.config.SqlTraceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLineSqlTraceStoreTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T05:00:00Z"), ZONE);
    private JsonLineSqlTraceStore store;

    @BeforeEach
    void setUp() {
        SqlTraceProperties properties = new SqlTraceProperties();
        properties.setDirectory(tempDirectory);
        store = new JsonLineSqlTraceStore(properties, objectMapper, clock);
    }

    @Test
    void appendsAndFindsOnlyMatchingRequestAndPage() throws Exception {
        store.append(entry("request-1", "page01", "select 1"));
        store.append(entry("request-1", "page02", "select 2"));
        store.append(entry("request-2", "page01", "select 3"));

        assertThat(store.find("request-1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 1");
    }

    @Test
    void readsTodayAndPreviousDayFiles() throws Exception {
        writeEntryFor(LocalDate.of(2026, 6, 24), entry("request-old", "page01", "select old"));
        writeEntryFor(LocalDate.of(2026, 6, 25), entry("request-new", "page01", "select new"));

        assertThat(store.find("request-old", "page01")).hasSize(1);
        assertThat(store.find("request-new", "page01")).hasSize(1);
    }

    @Test
    void skipsMalformedLines() throws Exception {
        Files.writeString(todayFile(), "{broken}\n", UTF_8, CREATE, APPEND);
        store.append(entry("request-1", "page01", "select 1"));

        assertThat(store.find("request-1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 1");
    }

    @Test
    void concurrentWritesRemainOneJsonObjectPerLine() throws Exception {
        int count = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int index = 0; index < count; index++) {
            int current = index;
            executor.submit(() -> {
                try {
                    store.append(entry(
                            "request-" + current,
                            "page01",
                            "select " + current));
                } catch (java.io.IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        List<String> lines = Files.readAllLines(todayFile(), UTF_8);
        assertThat(lines).hasSize(count);
        assertThat(lines).allSatisfy(line ->
                assertThatCode(() -> objectMapper.readTree(line)).doesNotThrowAnyException());
    }

    @Test
    void lookupWaitsForAnInProgressAppend() throws Exception {
        BlockingObjectMapper blockingMapper = new BlockingObjectMapper();
        SqlTraceProperties properties = new SqlTraceProperties();
        properties.setDirectory(tempDirectory);
        JsonLineSqlTraceStore blockingStore =
                new JsonLineSqlTraceStore(properties, blockingMapper, clock);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> append = executor.submit(() -> {
            try {
                blockingStore.append(entry("request-1", "page01", "select 1"));
            } catch (java.io.IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        assertThat(blockingMapper.writeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Future<List<SqlTraceEntry>> lookup =
                executor.submit(() -> blockingStore.find("request-1", "page01"));
        assertThatCode(() -> {
            try {
                lookup.get(100, TimeUnit.MILLISECONDS);
                throw new AssertionError("lookup completed before append");
            } catch (TimeoutException expected) {
                // Expected while append owns the file I/O lock.
            }
        }).doesNotThrowAnyException();

        blockingMapper.allowWrite.countDown();
        append.get(5, TimeUnit.SECONDS);
        assertThat(lookup.get(5, TimeUnit.SECONDS)).hasSize(1);
        executor.shutdownNow();
    }

    private SqlTraceEntry entry(String requestId, String pageId, String sql) {
        return new SqlTraceEntry(
                requestId,
                pageId,
                OffsetDateTime.parse("2026-06-25T14:20:31+09:00"),
                4,
                sql);
    }

    private void writeEntryFor(LocalDate date, SqlTraceEntry entry) throws Exception {
        Files.writeString(
                fileFor(date),
                objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                UTF_8,
                CREATE,
                APPEND);
    }

    private Path todayFile() {
        return fileFor(LocalDate.now(clock));
    }

    private Path fileFor(LocalDate date) {
        return tempDirectory.resolve("sql-trace-" + date + ".jsonl");
    }

    private static class BlockingObjectMapper extends ObjectMapper {

        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch allowWrite = new CountDownLatch(1);

        private BlockingObjectMapper() {
            findAndRegisterModules();
        }

        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            writeStarted.countDown();
            try {
                if (!allowWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to continue write");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return super.writeValueAsString(value);
        }
    }
}
