package com.example.admin.common.redis;

import org.springframework.util.StringUtils;

public record RedisKey(
        RedisNamespace namespace,
        String userId,
        String uiId) {

    public RedisKey {
        if (namespace == null) {
            throw new IllegalArgumentException("Redis namespace is required.");
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Redis userId is required.");
        }
        if (!StringUtils.hasText(uiId)) {
            throw new IllegalArgumentException("Redis uiId is required.");
        }
        userId = userId.trim();
        uiId = uiId.trim();
    }

    public String value() {
        return namespace.prefix() + ":" + userId + ":" + uiId;
    }
}
