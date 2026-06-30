package com.example.admin.sqltrace.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.example.admin.common.redis.RedisKey;
import com.example.admin.common.redis.RedisStore;
import com.example.admin.sqltrace.context.SqlTraceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RedisSqlTraceStoreTests {

    private static final OffsetDateTime FIRST_API_STARTED_AT =
            OffsetDateTime.parse("2026-07-01T10:30:15.100+09:00");
    private static final OffsetDateTime SECOND_API_STARTED_AT =
            FIRST_API_STARTED_AT.plusSeconds(1);
    private static final OffsetDateTime EXECUTED_AT =
            OffsetDateTime.parse("2026-07-01T10:30:15.200+09:00");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryRedisStore redis = new InMemoryRedisStore(objectMapper);
    private final RedisSqlTraceStore store = new RedisSqlTraceStore(redis, objectMapper);

    @Test
    void storesOneRedisListElementPerSelectQuery() throws Exception {
        store.append(query(FIRST_API_STARTED_AT, "select 1", 4));
        store.append(query(FIRST_API_STARTED_AT, "select 2", 7));

        List<String> stored = redis.lists.get("query:user1:page01");

        assertThat(stored).hasSize(2);
        assertThat(stored.get(0))
                .contains("\"sql\":\"select 2\"")
                .contains("\"apiStartedAt\":\"2026-07-01T10:30:15.1+09:00\"")
                .doesNotContain("\"queries\"")
                .doesNotContain("\"requestId\"")
                .doesNotContain("\"eventType\"");
        assertThat(stored.get(1)).contains("\"sql\":\"select 1\"");
        assertThat(redis.ttls.get("query:user1:page01")).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void returnsNewestQueriesFirstAcrossApiExecutions() throws Exception {
        store.append(query(FIRST_API_STARTED_AT, "older query", 4));
        store.append(query(SECOND_API_STARTED_AT, "newer query", 5));

        assertThat(store.find(SqlTraceType.QUERY, "user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("newer query", "older query");
    }

    @Test
    void updatesServerTimeOnEveryQueryFromTheSameApiExecution() throws Exception {
        store.append(query(FIRST_API_STARTED_AT, "select 1", 4));
        store.append(query(FIRST_API_STARTED_AT, "select 2", 7));
        store.append(query(SECOND_API_STARTED_AT, "select 3", 2));

        long updated = store.updateServerTiming(
                SqlTraceType.QUERY,
                "user1",
                "page01",
                FIRST_API_STARTED_AT,
                500);

        assertThat(updated).isEqualTo(2);
        List<SqlTraceEntry> entries = store.find(SqlTraceType.QUERY, "user1", "page01");
        assertThat(entries)
                .filteredOn(entry -> entry.apiStartedAt().equals(FIRST_API_STARTED_AT))
                .extracting(SqlTraceEntry::serverTimeMillis)
                .containsOnly(500L);
        assertThat(entries)
                .filteredOn(entry -> entry.apiStartedAt().equals(SECOND_API_STARTED_AT))
                .extracting(SqlTraceEntry::serverTimeMillis)
                .containsOnlyNulls();
    }

    @Test
    void updatesClientAndTotalTimeWithoutAddingOrReorderingObjects() throws Exception {
        store.append(query(FIRST_API_STARTED_AT, "select 1", 4));
        store.append(query(FIRST_API_STARTED_AT, "select 2", 7));
        List<String> before = List.copyOf(redis.lists.get("query:user1:page01"));

        long updated = store.updateClientTiming(
                SqlTraceType.QUERY,
                "user1",
                "page01",
                FIRST_API_STARTED_AT,
                300.0,
                1000.0);

        assertThat(updated).isEqualTo(2);
        assertThat(redis.lists.get("query:user1:page01")).hasSameSizeAs(before);
        assertThat(store.find(SqlTraceType.QUERY, "user1", "page01"))
                .extracting(SqlTraceEntry::sql)
                .containsExactly("select 2", "select 1");
        assertThat(store.find(SqlTraceType.QUERY, "user1", "page01"))
                .allSatisfy(entry -> {
                    assertThat(entry.clientTimeMillis()).isEqualTo(300.0);
                    assertThat(entry.totalTimeMillis()).isEqualTo(1000.0);
                });
    }

    @Test
    void deleteRemovesTheTypedUserPageList() throws Exception {
        store.append(query(FIRST_API_STARTED_AT, "select 1", 4));

        store.delete(SqlTraceType.QUERY, "user1", "page01");

        assertThat(store.find(SqlTraceType.QUERY, "user1", "page01")).isEmpty();
        assertThat(redis.deletedKey).isEqualTo("query:user1:page01");
    }

    @Test
    void wrapsRedisClientFailuresAsIoExceptions() {
        redis.failure = new IOException("redis down");

        assertThatIOException()
                .isThrownBy(() -> store.append(query(
                        FIRST_API_STARTED_AT,
                        "select 1",
                        4)))
                .withMessageContaining("redis down");
        assertThatIOException()
                .isThrownBy(() -> store.find(SqlTraceType.QUERY, "user1", "page01"))
                .withMessageContaining("redis down");
        assertThatIOException()
                .isThrownBy(() -> store.updateClientTiming(
                        SqlTraceType.QUERY,
                        "user1",
                        "page01",
                        FIRST_API_STARTED_AT,
                        300,
                        1000))
                .withMessageContaining("redis down");
    }

    private SqlTraceEntry query(
            OffsetDateTime apiStartedAt,
            String sql,
            long sqlElapsedMillis) {
        return SqlTraceEntry.query(
                SqlTraceType.QUERY,
                "user1",
                apiStartedAt,
                "page01",
                EXECUTED_AT,
                sqlElapsedMillis,
                sql);
    }

    private static class InMemoryRedisStore implements RedisStore {

        private final ObjectMapper objectMapper;
        private final Map<String, List<String>> lists = new ConcurrentHashMap<>();
        private final Map<String, Duration> ttls = new ConcurrentHashMap<>();
        private String deletedKey;
        private IOException failure;

        private InMemoryRedisStore(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void append(RedisKey key, String value, Duration ttl) throws IOException {
            throwIfFailed();
            lists.computeIfAbsent(
                    key.value(),
                    ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .add(0, value);
            ttls.put(key.value(), ttl);
        }

        @Override
        public long updateByApiStartedAt(
                RedisKey key,
                String apiStartedAt,
                String updatesJson,
                Duration ttl) throws IOException {
            throwIfFailed();
            List<String> values = lists.get(key.value());
            if (values == null) {
                return 0;
            }
            JsonNode updates = objectMapper.readTree(updatesJson);
            long updated = 0;
            synchronized (values) {
                for (int index = 0; index < values.size(); index++) {
                    ObjectNode entry = (ObjectNode) objectMapper.readTree(values.get(index));
                    if (!apiStartedAt.equals(entry.path("apiStartedAt").asText())) {
                        continue;
                    }
                    updates.properties().forEach(field ->
                            entry.set(field.getKey(), field.getValue()));
                    values.set(index, objectMapper.writeValueAsString(entry));
                    updated++;
                }
            }
            if (updated > 0) {
                ttls.put(key.value(), ttl);
            }
            return updated;
        }

        @Override
        public List<String> findAll(RedisKey key) throws IOException {
            throwIfFailed();
            return List.copyOf(lists.getOrDefault(key.value(), List.of()));
        }

        @Override
        public void delete(RedisKey key) throws IOException {
            throwIfFailed();
            deletedKey = key.value();
            lists.remove(key.value());
            ttls.remove(key.value());
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
