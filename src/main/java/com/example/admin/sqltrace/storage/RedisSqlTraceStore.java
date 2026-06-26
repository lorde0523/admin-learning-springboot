package com.example.admin.sqltrace.storage;

import com.example.admin.common.redis.RedisKey;
import com.example.admin.common.redis.RedisNamespace;
import com.example.admin.common.redis.RedisStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class RedisSqlTraceStore implements SqlTraceStore {

    private final RedisStore redisStore;
    private final ObjectMapper objectMapper;
    private final JavaType entryListType;
    private final ReentrantLock lock = new ReentrantLock();

    public RedisSqlTraceStore(RedisStore redisStore, ObjectMapper objectMapper) {
        this.redisStore = redisStore;
        this.objectMapper = objectMapper;
        this.entryListType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, SqlTraceEntry.class);
    }

    @Override
    public void append(SqlTraceEntry entry) throws IOException {
        lock.lock();
        try {
            List<SqlTraceEntry> entries = readEntries(entry.username(), entry.uiId());
            entries.add(entry);
            saveEntries(entry.username(), entry.uiId(), entries);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<SqlTraceEntry> find(String username, String uiId) throws IOException {
        lock.lock();
        try {
            List<SqlTraceEntry> queries = new ArrayList<>();
            Map<String, SqlTraceEntry> timings = new HashMap<>();
            for (SqlTraceEntry entry : readEntries(username, uiId)) {
                if (!belongsTo(entry, username, uiId)) {
                    continue;
                }
                if (entry.eventType() == SqlTraceEventType.QUERY) {
                    queries.add(entry);
                } else if (entry.eventType() == SqlTraceEventType.TIMING
                        && entry.requestId() != null) {
                    timings.put(entry.requestId(), entry);
                }
            }
            return queries.stream()
                    .map(query -> query.withTiming(timings.get(query.requestId())))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean appendTimingIfOwned(SqlTraceEntry timing) throws IOException {
        lock.lock();
        try {
            List<SqlTraceEntry> entries = readEntries(timing.username(), timing.uiId());
            boolean hasVisibleQuery = entries.stream()
                    .anyMatch(entry -> entry.eventType() == SqlTraceEventType.QUERY
                            && timing.username().equals(entry.username())
                            && timing.uiId().equals(entry.uiId())
                            && timing.requestId().equals(entry.requestId()));
            if (!hasVisibleQuery) {
                return false;
            }
            entries.add(timing);
            saveEntries(timing.username(), timing.uiId(), entries);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String username, String uiId) throws IOException {
        lock.lock();
        try {
            redisStore.delete(key(username, uiId));
        } finally {
            lock.unlock();
        }
    }

    private List<SqlTraceEntry> readEntries(String username, String uiId) throws IOException {
        String value = redisStore.get(key(username, uiId)).orElse(null);
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(value, entryListType));
        } catch (JsonProcessingException exception) {
            throw new IOException("Failed to read SQL trace logs from Redis.", exception);
        }
    }

    private void saveEntries(String username, String uiId, List<SqlTraceEntry> entries)
            throws IOException {
        try {
            redisStore.save(key(username, uiId), objectMapper.writeValueAsString(entries));
        } catch (JsonProcessingException exception) {
            throw new IOException("Failed to write SQL trace logs to Redis.", exception);
        }
    }

    private boolean belongsTo(SqlTraceEntry entry, String username, String uiId) {
        return entry != null
                && entry.eventType() != null
                && username.equals(entry.username())
                && uiId.equals(entry.uiId());
    }

    private RedisKey key(String username, String uiId) {
        return new RedisKey(RedisNamespace.QUERY, username, uiId);
    }
}
