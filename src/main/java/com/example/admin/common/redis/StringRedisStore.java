package com.example.admin.common.redis;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class StringRedisStore implements RedisStore {

    private static final RedisScript<Long> APPEND_AND_EXPIRE = new DefaultRedisScript<>(
            """
            redis.call('LPUSH', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """,
            Long.class);

    private static final RedisScript<Long> UPDATE_BY_API_STARTED_AT =
            new DefaultRedisScript<>(
                    """
                    local values = redis.call('LRANGE', KEYS[1], 0, -1)
                    local updates = cjson.decode(ARGV[2])
                    local updated = 0
                    for index, value in ipairs(values) do
                        local entry = cjson.decode(value)
                        if entry.apiStartedAt == ARGV[1] then
                            for field, newValue in pairs(updates) do
                                entry[field] = newValue
                            end
                            redis.call('LSET', KEYS[1], index - 1, cjson.encode(entry))
                            updated = updated + 1
                        end
                    end
                    if updated > 0 then
                        redis.call('EXPIRE', KEYS[1], ARGV[3])
                    end
                    return updated
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    public StringRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void append(RedisKey key, String value, Duration ttl) throws IOException {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis TTL must be positive.");
        }
        try {
            redisTemplate.execute(
                    APPEND_AND_EXPIRE,
                    List.of(key.value()),
                    value,
                    Long.toString(ttl.toSeconds()));
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to append value to Redis: " + exception.getMessage(),
                    exception);
        }
    }

    @Override
    public List<String> findAll(RedisKey key) throws IOException {
        try {
            List<String> values = redisTemplate.opsForList().range(key.value(), 0, -1);
            return values == null ? List.of() : List.copyOf(values);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to read values from Redis.", exception);
        }
    }

    @Override
    public long updateByApiStartedAt(
            RedisKey key,
            String apiStartedAt,
            String updatesJson,
            Duration ttl) throws IOException {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis TTL must be positive.");
        }
        try {
            Long updated = redisTemplate.execute(
                    UPDATE_BY_API_STARTED_AT,
                    List.of(key.value()),
                    apiStartedAt,
                    updatesJson,
                    Long.toString(ttl.toSeconds()));
            return updated == null ? 0 : updated;
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to update Redis values: " + exception.getMessage(),
                    exception);
        }
    }

    @Override
    public void delete(RedisKey key) throws IOException {
        try {
            redisTemplate.delete(key.value());
        } catch (RuntimeException exception) {
            throw new IOException("Failed to delete value from Redis.", exception);
        }
    }
}
