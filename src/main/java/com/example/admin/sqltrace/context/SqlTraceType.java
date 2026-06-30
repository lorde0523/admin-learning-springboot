package com.example.admin.sqltrace.context;

import com.example.admin.common.redis.RedisNamespace;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum SqlTraceType {
    QUERY("query", RedisNamespace.QUERY);

    private final String value;
    private final RedisNamespace namespace;

    SqlTraceType(String value, RedisNamespace namespace) {
        this.value = value;
        this.namespace = namespace;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public RedisNamespace namespace() {
        return namespace;
    }

    public static Optional<SqlTraceType> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(normalized))
                .findFirst();
    }

    @JsonCreator
    public static SqlTraceType parse(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported SQL trace type: " + value));
    }
}
