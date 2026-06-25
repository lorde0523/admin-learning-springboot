package com.example.admin.sqltrace.api;

import java.time.OffsetDateTime;

public record SqlTraceLogRow(
        String requestId,
        String pageId,
        OffsetDateTime executedAt,
        long sqlElapsedMillis,
        Double clientApiElapsedMillis,
        Double clientTotalElapsedMillis,
        String sql) {
}
