package com.example.admin.sqltrace.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.example.admin.common.redis.RedisKey;
import com.example.admin.common.redis.RedisStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedisSqlTraceStoreTests {

    private static final OffsetDateTime TIME =
            OffsetDateTime.parse("2026-06-25T14:20:31+09:00");

    private final InMemoryRedisStore redis = new InMemoryRedisStore();
    private final RedisSqlTraceStore store =
            new RedisSqlTraceStore(redis, new ObjectMapper().findAndRegisterModules());

    @Test
    void appendsQueriesToAUserPageValueAndReadsThemBack() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));
        store.append(query("user1", "request-2", "page01", "select 2"));
        store.append(query("user2", "request-3", "page01", "select 3"));

        assertThat(store.find("user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 1", "select 2");
        assertThat(redis.values).containsKey("query:user1:page01");
    }

    @Test
    void combinesTimingWhenTheRequestBelongsToTheSameUserAndPage() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));

        boolean appended = store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user1", "request-1", "page01", TIME.plusSeconds(1), 35.2, 48.7));

        assertThat(appended).isTrue();
        assertThat(store.find("user1", "page01")).singleElement().satisfies(entry -> {
            assertThat(entry.clientApiElapsedMillis()).isEqualTo(35.2);
            assertThat(entry.clientTotalElapsedMillis()).isEqualTo(48.7);
        });
    }

    @Test
    void rejectsTimingWhenTheQueryDoesNotBelongToTheSameUserAndPage() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));

        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user2", "request-1", "page01", TIME.plusSeconds(1), 35.2, 48.7)))
                .isFalse();
        assertThat(store.appendTimingIfOwned(SqlTraceEntry.timing(
                "user1", "request-1", "page02", TIME.plusSeconds(1), 35.2, 48.7)))
                .isFalse();
    }

    @Test
    void deleteRemovesTheUserPageValue() throws Exception {
        store.append(query("user1", "request-1", "page01", "select 1"));

        store.delete("user1", "page01");

        assertThat(store.find("user1", "page01")).isEmpty();
        assertThat(redis.deletedKey).isEqualTo("query:user1:page01");
    }

    @Test
    void wrapsRedisClientFailuresAsIoExceptions() {
        redis.failure = new IOException("redis down");

        assertThatIOException()
                .isThrownBy(() -> store.append(query("user1", "request-1", "page01", "select 1")))
                .withMessageContaining("redis down");
        assertThatIOException()
                .isThrownBy(() -> store.find("user1", "page01"))
                .withMessageContaining("redis down");
        assertThatIOException()
                .isThrownBy(() -> store.delete("user1", "page01"))
                .withMessageContaining("redis down");
    }

    private SqlTraceEntry query(String username, String requestId, String pageId, String sql) {
        return SqlTraceEntry.query(username, requestId, pageId, TIME, 4, sql);
    }

    private static class InMemoryRedisStore implements RedisStore {

        private final Map<String, String> values = new HashMap<>();
        private String deletedKey;
        private IOException failure;

        @Override
        public Optional<String> get(RedisKey key) throws IOException {
            throwIfFailed();
            return Optional.ofNullable(values.get(key.value()));
        }

        @Override
        public void save(RedisKey key, String value) throws IOException {
            throwIfFailed();
            values.put(key.value(), value);
        }

        @Override
        public void delete(RedisKey key) throws IOException {
            throwIfFailed();
            deletedKey = key.value();
            values.remove(key.value());
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
