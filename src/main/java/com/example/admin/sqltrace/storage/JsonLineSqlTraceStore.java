package com.example.admin.sqltrace.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import com.example.admin.sqltrace.config.SqlTraceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonLineSqlTraceStore implements SqlTraceStore {

    private static final Logger log = LoggerFactory.getLogger(JsonLineSqlTraceStore.class);

    private final Path directory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ReentrantLock ioLock = new ReentrantLock();

    public JsonLineSqlTraceStore(
            SqlTraceProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.directory = properties.getDirectory();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(SqlTraceEntry entry) throws IOException {
        ioLock.lock();
        try {
            Files.createDirectories(directory);
            Files.writeString(
                    fileFor(LocalDate.now(clock)),
                    objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    UTF_8,
                    CREATE,
                    WRITE,
                    APPEND);
        } finally {
            ioLock.unlock();
        }
    }

    @Override
    public List<SqlTraceEntry> find(String username, String pageId) throws IOException {
        ioLock.lock();
        try {
            List<SqlTraceEntry> queries = new ArrayList<>();
            Map<String, SqlTraceEntry> timings = new HashMap<>();
            for (Path file : traceFiles()) {
                readEvents(file, username, pageId, queries, timings);
            }
            return queries.stream()
                    .map(query -> query.withTiming(timings.get(query.requestId())))
                    .toList();
        } finally {
            ioLock.unlock();
        }
    }

    @Override
    public boolean appendTimingIfOwned(SqlTraceEntry timing) throws IOException {
        ioLock.lock();
        try {
            if (!hasVisibleQuery(timing.username(), timing.requestId(), timing.pageId())) {
                return false;
            }
            Files.createDirectories(directory);
            Files.writeString(
                    fileFor(LocalDate.now(clock)),
                    objectMapper.writeValueAsString(timing) + System.lineSeparator(),
                    UTF_8,
                    CREATE,
                    WRITE,
                    APPEND);
            return true;
        } finally {
            ioLock.unlock();
        }
    }

    private boolean hasVisibleQuery(String username, String requestId, String pageId)
            throws IOException {
        List<SqlTraceEntry> queries = new ArrayList<>();
        Map<String, SqlTraceEntry> timings = new HashMap<>();
        for (Path file : traceFiles()) {
            readEvents(file, username, pageId, queries, timings);
        }
        return queries.stream().anyMatch(entry -> requestId.equals(entry.requestId()));
    }

    private List<Path> traceFiles() throws IOException {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(
                            "sql-trace-\\d{4}-\\d{2}-\\d{2}\\.jsonl"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private void readEvents(
            Path file,
            String username,
            String pageId,
            List<SqlTraceEntry> queries,
            Map<String, SqlTraceEntry> timings) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                SqlTraceEntry entry = parse(line, file);
                if (!belongsTo(entry, username, pageId)) {
                    continue;
                }
                if (entry.eventType() == SqlTraceEventType.CLEAR) {
                    queries.clear();
                    timings.clear();
                } else if (entry.eventType() == SqlTraceEventType.QUERY) {
                    queries.add(entry);
                } else if (entry.eventType() == SqlTraceEventType.TIMING
                        && entry.requestId() != null) {
                    timings.put(entry.requestId(), entry);
                }
            }
        }
    }

    private SqlTraceEntry parse(String line, Path file) {
        try {
            return objectMapper.readValue(line, SqlTraceEntry.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Skipping malformed SQL trace line. file={}", file, exception);
            return null;
        }
    }

    private boolean belongsTo(SqlTraceEntry entry, String username, String pageId) {
        return entry != null
                && entry.eventType() != null
                && username.equals(entry.username())
                && pageId.equals(entry.pageId());
    }

    private Path fileFor(LocalDate date) {
        return directory.resolve("sql-trace-" + date + ".jsonl");
    }
}
