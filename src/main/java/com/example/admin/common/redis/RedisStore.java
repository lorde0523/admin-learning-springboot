package com.example.admin.common.redis;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface RedisStore {

    void append(RedisKey key, String value, Duration ttl) throws IOException;

    long updateByApiStartedAt(
            RedisKey key,
            String apiStartedAt,
            String updatesJson,
            Duration ttl) throws IOException;

    List<String> findAll(RedisKey key) throws IOException;

    void delete(RedisKey key) throws IOException;
}
