package com.example.admin.sqltrace.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.admin.sqltrace.context.SqlCaptureContext;
import com.example.admin.sqltrace.context.SqlCaptureContextHolder;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SqlTraceRecorderTests {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-06-25T14:20:31+09:00");

    private final CapturingStore store = new CapturingStore();
    private final SqlTraceRecorder recorder = new SqlTraceRecorder(
            new SqlParameterRenderer(new NoOpSqlValueMasker()),
            store);

    @AfterEach
    void clearContext() {
        SqlCaptureContextHolder.clear();
    }

    @Test
    void recordsRenderedSqlForActiveContext() {
        SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

        recorder.record(
                "select * from admin_menu where menu_name = ?",
                Map.of(1, "Admin"),
                12_400_000L,
                NOW);

        assertThat(store.entries).singleElement().satisfies(entry -> {
            assertThat(entry.requestId()).isEqualTo("request-1");
            assertThat(entry.pageId()).isEqualTo("page01");
            assertThat(entry.elapsedMillis()).isEqualTo(12);
            assertThat(entry.sql()).contains("menu_name = 'Admin'");
        });
    }

    @Test
    void skipsPausedOrMissingContext() {
        recorder.record("select 1", Map.of(), 1_000_000L, NOW);
        assertThat(store.entries).isEmpty();

        SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", true));
        recorder.record("select 1", Map.of(), 1_000_000L, NOW);
        assertThat(store.entries).isEmpty();
    }

    @Test
    void storageFailureDoesNotEscape() {
        store.failure = new IOException("disk full");
        SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

        assertThatCode(() -> recorder.record("select 1", Map.of(), 1_000_000L, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsNonSelectSqlEvenWithActiveContext() {
        SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

        recorder.record("update admin_menu set enabled = false", Map.of(), 1_000_000L, NOW);
        recorder.record("call some_procedure()", Map.of(), 1_000_000L, NOW);

        assertThat(store.entries).isEmpty();
    }

    @Test
    void recordsSelectAndCommonTableExpressionQueries() {
        SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

        recorder.record("  SELECT 1", Map.of(), 1_000_000L, NOW);
        recorder.record("with menu_rows as (select 1) select * from menu_rows", Map.of(), 1_000_000L, NOW);

        assertThat(store.entries).hasSize(2);
    }

    private static class CapturingStore implements SqlTraceStore {

        private final List<SqlTraceEntry> entries = new ArrayList<>();
        private IOException failure;

        @Override
        public void append(SqlTraceEntry entry) throws IOException {
            if (failure != null) {
                throw failure;
            }
            entries.add(entry);
        }

        @Override
        public List<SqlTraceEntry> find(String requestId, String pageId) {
            return List.of();
        }
    }
}
