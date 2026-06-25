package com.example.admin.sqltrace.jdbc;

import com.example.admin.sqltrace.context.SqlCaptureContext;
import com.example.admin.sqltrace.context.SqlCaptureContextHolder;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(SqlTraceRecorder.class);

    private final SqlParameterRenderer renderer;
    private final SqlTraceStore store;

    public SqlTraceRecorder(SqlParameterRenderer renderer, SqlTraceStore store) {
        this.renderer = renderer;
        this.store = store;
    }

    public void record(
            String sql,
            Map<Integer, Object> parameters,
            long elapsedNanos,
            OffsetDateTime executedAt) {
        Optional<SqlCaptureContext> current = SqlCaptureContextHolder.current();
        if (current.isEmpty() || current.get().sqlCapturePaused() || !isSelectQuery(sql)) {
            return;
        }

        SqlCaptureContext context = current.get();
        SqlTraceEntry entry = SqlTraceEntry.query(
                context.username(),
                context.requestId(),
                context.pageId(),
                executedAt,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                renderer.render(sql, parameters));
        try {
            store.append(entry);
        } catch (IOException exception) {
            log.warn(
                    "Failed to append SQL trace. requestId={} pageId={}",
                    entry.requestId(),
                    entry.pageId(),
                    exception);
        }
    }

    private boolean isSelectQuery(String sql) {
        String remaining = sql.stripLeading();
        while (true) {
            if (remaining.startsWith("--")) {
                int lineEnd = remaining.indexOf('\n');
                if (lineEnd < 0) {
                    return false;
                }
                remaining = remaining.substring(lineEnd + 1).stripLeading();
                continue;
            }
            if (remaining.startsWith("/*")) {
                int commentEnd = remaining.indexOf("*/", 2);
                if (commentEnd < 0) {
                    return false;
                }
                remaining = remaining.substring(commentEnd + 2).stripLeading();
                continue;
            }
            return startsWithKeyword(remaining, "select") || startsWithKeyword(remaining, "with");
        }
    }

    private boolean startsWithKeyword(String sql, String keyword) {
        if (!sql.regionMatches(true, 0, keyword, 0, keyword.length())) {
            return false;
        }
        return sql.length() == keyword.length()
                || !Character.isJavaIdentifierPart(sql.charAt(keyword.length()));
    }
}
