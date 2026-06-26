package com.example.admin.common.redis;

import java.io.IOException;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class StringRedisStore implements RedisStore {

    private final StringRedisTemplate redisTemplate;

    public StringRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(RedisKey key) throws IOException {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key.value()));
        } catch (RuntimeException exception) {
            throw new IOException("Failed to read value from Redis.", exception);
        }
    }

    @Override
    public void save(RedisKey key, String value) throws IOException {
        try {
            redisTemplate.opsForValue().set(key.value(), value);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to save value to Redis.", exception);
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
