package com.example.admin.sqltrace.api;

import java.time.OffsetDateTime;

public record SqlTraceLogRow(
        String pageId,
        OffsetDateTime executedAt,
        long elapsedMillis,
        String sql) {
}
