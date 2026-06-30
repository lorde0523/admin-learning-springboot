package com.example.admin.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@SuppressWarnings("unchecked")
class StringRedisStoreTests {

    private final StringRedisTemplate redisTemplate =
            org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final StringRedisStore store = new StringRedisStore(redisTemplate);

    @Test
    void prependsAndRefreshesTtlInOneRedisScript() throws Exception {
        RedisKey key = new RedisKey(RedisNamespace.QUERY, "user1", "page01");

        store.append(key, "{\"sql\":\"select 1\"}", Duration.ofHours(24));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("query:user1:page01")),
                eq("{\"sql\":\"select 1\"}"),
                eq("86400"));
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("LPUSH")
                .contains("EXPIRE");
    }

    @Test
    void updatesEveryEntryWithTheSameApiStartedAtWithoutChangingListOrder() throws Exception {
        RedisKey key = new RedisKey(RedisNamespace.QUERY, "user1", "page01");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("query:user1:page01")),
                eq("2026-07-01T10:30:15.100+09:00"),
                eq("{\"clientTimeMillis\":300.0,\"totalTimeMillis\":1000.0}"),
                eq("86400")))
                .thenReturn(2L);

        long updated = store.updateByApiStartedAt(
                key,
                "2026-07-01T10:30:15.100+09:00",
                "{\"clientTimeMillis\":300.0,\"totalTimeMillis\":1000.0}",
                Duration.ofHours(24));

        assertThat(updated).isEqualTo(2);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("query:user1:page01")),
                eq("2026-07-01T10:30:15.100+09:00"),
                eq("{\"clientTimeMillis\":300.0,\"totalTimeMillis\":1000.0}"),
                eq("86400"));
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("LRANGE")
                .contains("LSET")
                .doesNotContain("LPUSH");
    }

    @Test
    void readsEveryListEntryInInsertionOrder() throws Exception {
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations =
                org.mockito.Mockito.mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("query:user1:page01", 0, -1))
                .thenReturn(List.of("first", "second"));

        List<String> result = store.findAll(
                new RedisKey(RedisNamespace.QUERY, "user1", "page01"));

        assertThat(result).containsExactly("first", "second");
    }

    @Test
    void wrapsRedisFailuresAsIoExceptions() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("query:user1:page01")),
                any(),
                any()))
                .thenThrow(new IllegalStateException("redis down"));

        org.assertj.core.api.Assertions.assertThatIOException()
                .isThrownBy(() -> store.append(
                        new RedisKey(RedisNamespace.QUERY, "user1", "page01"),
                        "value",
                        Duration.ofHours(24)))
                .withMessageContaining("redis down");
    }
}
