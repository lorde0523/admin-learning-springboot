package com.example.admin.sqltrace.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.admin.sqltrace.config.SqlTraceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLineSqlTraceStoreTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime TIME =
            OffsetDateTime.parse("2026-06-25T14:20:31+09:00");

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-25T05:00:00Z"), ZONE);
    private JsonLineSqlTraceStore store;

    @BeforeEach
    void setUp() {
        store = newStore(objectMapper);
    }

    @Test
    void returnsAccumulatedQueriesOnlyForCurrentUserAndPage() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));
        store.append(query("user1", "request-2", "page01", "select 2"));
        store.append(query("user1", "request-3", "page02", "select 3"));
        store.append(query("user2", "request-4", "page01", "select 4"));

        assertThat(store.find("user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 1", "select 2");
        assertThat(store.find("user2", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 4");
    }

    @Test
    void combinesClientTimingWithEveryQueryFromTheSameRequest() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));
        store.append(query("user1", "request-1", "page01", "select 2"));
        store.append(SqlTraceEntry.timing(
                "user1", "request-1", "page01", TIME.plusSeconds(1), 35.2, 48.7));

        assertThat(store.find("user1", "page01")).allSatisfy(entry -> {
            assertThat(entry.clientApiElapsedMillis()).isEqualTo(35.2);
            assertThat(entry.clientTotalElapsedMillis()).isEqualTo(48.7);
        });
    }

    @Test
    void clearHidesPreviousQueriesAndKeepsLaterQueries() throws Exception {
        store.append(query("user1", "request-old", "page01", "select old"));
        store.append(query("user2", "request-other", "page01", "select other"));
        store.append(SqlTraceEntry.clear("user1", "page01", TIME.plusMinutes(1)));
        store.append(query(
                "user1",
                "request-new",
                "page01",
                "select new",
                TIME.plusMinutes(2)));

        assertThat(store.find("user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select new");
        assertThat(store.find("user2", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select other");
    }

    @Test
    void clearMarkerSurvivesStoreRecreation() throws Exception {
        store.append(query("user1", "request-old", "page01", "select old"));
        store.append(SqlTraceEntry.clear("user1", "page01", TIME.plusMinutes(1)));

        JsonLineSqlTraceStore recreated = newStore(objectMapper);

        assertThat(recreated.find("user1", "page01")).isEmpty();
    }

    @Test
    void appendTimingIfOwnedChecksUserRequestAndPageOwnership() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));

        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user1", "request-1", "page01", TIME.plusSeconds(1), 1, 2))).isTrue();
        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user2", "request-1", "page01", TIME.plusSeconds(1), 1, 2))).isFalse();
        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user1", "request-1", "page02", TIME.plusSeconds(1), 1, 2))).isFalse();
    }

    @Test
    void appendTimingIfOwnedRejectsClearedOrForeignQueries() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));
        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user2", "request-1", "page01", TIME.plusSeconds(1), 1, 2)))
                .isFalse();

        store.append(SqlTraceEntry.clear("user1", "page01", TIME.plusSeconds(2)));
        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user1", "request-1", "page01", TIME.plusSeconds(3), 1, 2)))
                .isFalse();
    }

    @Test
    void readsAllDatePartitionFilesInChronologicalOrder() throws Exception {
        writeEntryFor(
                LocalDate.of(2026, 6, 20),
                query("user1", "request-old", "page01", "select old"));
        writeEntryFor(
                LocalDate.of(2026, 6, 25),
                query("user1", "request-new", "page01", "select new"));

        assertThat(store.find("user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select old", "select new");
    }

    @Test
    void skipsMalformedAndLegacyOwnerlessLines() throws Exception {
        Files.writeString(todayFile(), "{broken}\n", UTF_8, CREATE, APPEND);
        Files.writeString(
                todayFile(),
                """
                {"requestId":"legacy","pageId":"page01","sql":"select legacy"}
                """,
                UTF_8,
                CREATE,
                APPEND);
        store.append(query("user1", "request-1", "page01", "select 1"));

        assertThat(store.find("user1", "page01"))
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
                    store.append(query(
                            "user1",
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
        JsonLineSqlTraceStore blockingStore = newStore(blockingMapper);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> append = executor.submit(() -> {
            try {
                blockingStore.append(query(
                        "user1", "request-1", "page01", "select 1"));
            } catch (java.io.IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        assertThat(blockingMapper.writeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Future<List<SqlTraceEntry>> lookup =
                executor.submit(() -> blockingStore.find("user1", "page01"));
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

    private JsonLineSqlTraceStore newStore(ObjectMapper mapper) {
        SqlTraceProperties properties = new SqlTraceProperties();
        properties.setDirectory(tempDirectory);
        return new JsonLineSqlTraceStore(properties, mapper, clock);
    }

    private SqlTraceEntry query(String username, String requestId, String pageId, String sql) {
        return query(username, requestId, pageId, sql, TIME);
    }

    private SqlTraceEntry query(
            String username,
            String requestId,
            String pageId,
            String sql,
            OffsetDateTime occurredAt) {
        return SqlTraceEntry.query(
                username, requestId, pageId, occurredAt, 4, sql);
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
