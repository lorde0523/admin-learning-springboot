package com.example.admin.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeyTests {

    @Test
    void combinesNamespaceUserAndUiId() {
        RedisKey key = new RedisKey(RedisNamespace.QUERY, "user1", "USER_SEARCH_MODAL");

        assertThat(key.value()).isEqualTo("query:user1:USER_SEARCH_MODAL");
    }

    @Test
    void rejectsBlankKeyParts() {
        assertThatThrownBy(() -> new RedisKey(null, "user1", "ui1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisKey(RedisNamespace.QUERY, " ", "ui1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisKey(RedisNamespace.QUERY, "user1", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
