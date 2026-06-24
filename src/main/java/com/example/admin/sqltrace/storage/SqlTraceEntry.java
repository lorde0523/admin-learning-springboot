package com.example.admin.sqltrace.storage;

import java.time.OffsetDateTime;

public record SqlTraceEntry(
        String requestId,
        String pageId,
        OffsetDateTime executedAt,
        long elapsedMillis,
        String sql) {
}
