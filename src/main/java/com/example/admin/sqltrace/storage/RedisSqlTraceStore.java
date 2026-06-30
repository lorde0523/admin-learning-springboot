package com.example.admin.sqltrace.storage;

import com.example.admin.common.redis.RedisKey;
import com.example.admin.common.redis.RedisStore;
import com.example.admin.sqltrace.context.SqlTraceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisSqlTraceStore implements SqlTraceStore {

    private static final Duration ENTRY_TTL = Duration.ofHours(24);

    private final RedisStore redisStore;
    private final ObjectMapper objectMapper;

    public RedisSqlTraceStore(RedisStore redisStore, ObjectMapper objectMapper) {
        this.redisStore = redisStore;
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @Override
    public void append(SqlTraceEntry entry) throws IOException {
        try {
            redisStore.append(
                    key(entry.traceType(), entry.userId(), entry.uiId()),
                    objectMapper.writeValueAsString(entry),
                    ENTRY_TTL);
        } catch (JsonProcessingException exception) {
            throw new IOException("Failed to write SQL trace logs to Redis.", exception);
        }
    }

    @Override
    public List<SqlTraceEntry> find(
            SqlTraceType traceType,
            String userId,
            String uiId) throws IOException {
        List<SqlTraceEntry> entries = new ArrayList<>();
        for (String value : redisStore.findAll(key(traceType, userId, uiId))) {
            try {
                SqlTraceEntry entry = objectMapper.readValue(value, SqlTraceEntry.class);
                if (belongsTo(entry, traceType, userId, uiId)) {
                    entries.add(entry);
                }
            } catch (JsonProcessingException exception) {
                throw new IOException("Failed to read SQL trace logs from Redis.", exception);
            }
        }
        return List.copyOf(entries);
    }

    @Override
    public long updateServerTiming(
            SqlTraceType traceType,
            String userId,
            String uiId,
            OffsetDateTime apiStartedAt,
            long serverTimeMillis) throws IOException {
        return update(
                traceType,
                userId,
                uiId,
                apiStartedAt,
                Map.of("serverTimeMillis", serverTimeMillis));
    }

    @Override
    public long updateClientTiming(
            SqlTraceType traceType,
            String userId,
            String uiId,
            OffsetDateTime apiStartedAt,
            double clientTimeMillis,
            double totalTimeMillis) throws IOException {
        return update(
                traceType,
                userId,
                uiId,
                apiStartedAt,
                Map.of(
                        "clientTimeMillis", clientTimeMillis,
                        "totalTimeMillis", totalTimeMillis));
    }

    @Override
    public void delete(SqlTraceType traceType, String userId, String uiId) throws IOException {
        redisStore.delete(key(traceType, userId, uiId));
    }

    private long update(
            SqlTraceType traceType,
            String userId,
            String uiId,
            OffsetDateTime apiStartedAt,
            Map<String, ? extends Number> updates) throws IOException {
        try {
            return redisStore.updateByApiStartedAt(
                    key(traceType, userId, uiId),
                    objectMapper.valueToTree(apiStartedAt).asText(),
                    objectMapper.writeValueAsString(updates),
                    ENTRY_TTL);
        } catch (JsonProcessingException exception) {
            throw new IOException("Failed to update SQL trace timing in Redis.", exception);
        }
    }

    private boolean belongsTo(
            SqlTraceEntry entry,
            SqlTraceType traceType,
            String userId,
            String uiId) {
        return entry != null
                && entry.traceType() == traceType
                && userId.equals(entry.userId())
                && uiId.equals(entry.uiId());
    }

    private RedisKey key(SqlTraceType traceType, String userId, String uiId) {
        return new RedisKey(traceType.namespace(), userId, uiId);
    }
}
