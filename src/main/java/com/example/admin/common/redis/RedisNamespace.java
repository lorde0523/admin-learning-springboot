package com.example.admin.common.redis;

public enum RedisNamespace {
    QUERY("query");

    private final String prefix;

    RedisNamespace(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
