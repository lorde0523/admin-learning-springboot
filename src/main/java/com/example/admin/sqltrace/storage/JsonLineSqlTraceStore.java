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
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
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
    public List<SqlTraceEntry> find(String requestId, String pageId) throws IOException {
        ioLock.lock();
        try {
            LocalDate today = LocalDate.now(clock);
            List<SqlTraceEntry> matches = new ArrayList<>();
            readMatches(fileFor(today), requestId, pageId, matches);
            readMatches(fileFor(today.minusDays(1)), requestId, pageId, matches);
            return List.copyOf(matches);
        } finally {
            ioLock.unlock();
        }
    }

    private void readMatches(
            Path file,
            String requestId,
            String pageId,
            List<SqlTraceEntry> matches) throws IOException {
        if (Files.notExists(file)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    SqlTraceEntry entry = objectMapper.readValue(line, SqlTraceEntry.class);
                    if (requestId.equals(entry.requestId()) && pageId.equals(entry.pageId())) {
                        matches.add(entry);
                    }
                } catch (JsonProcessingException | RuntimeException exception) {
                    log.warn("Skipping malformed SQL trace line. file={}", file, exception);
                }
            }
        }
    }

    private Path fileFor(LocalDate date) {
        return directory.resolve("sql-trace-" + date + ".jsonl");
    }
}
