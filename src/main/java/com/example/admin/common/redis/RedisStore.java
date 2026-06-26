package com.example.admin.common.redis;

import java.io.IOException;
import java.util.Optional;

public interface RedisStore {

    Optional<String> get(RedisKey key) throws IOException;

    void save(RedisKey key, String value) throws IOException;

    void delete(RedisKey key) throws IOException;
}
