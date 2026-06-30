package com.example.admin.sqltrace.storage;

import com.example.admin.sqltrace.context.SqlTraceType;
import java.time.OffsetDateTime;

public record SqlTraceEntry(
        SqlTraceType traceType,
        String userId,
        OffsetDateTime apiStartedAt,
        String uiId,
        OffsetDateTime occurredAt,
        Long sqlElapsedMillis,
        Long serverTimeMillis,
        Double clientTimeMillis,
        Double totalTimeMillis,
        String sql) {

    public static SqlTraceEntry query(
            SqlTraceType traceType,
            String userId,
            OffsetDateTime apiStartedAt,
            String uiId,
            OffsetDateTime occurredAt,
            long sqlElapsedMillis,
            String sql) {
        return new SqlTraceEntry(
                traceType,
                userId,
                apiStartedAt,
                uiId,
                occurredAt,
                sqlElapsedMillis,
                null,
                null,
                null,
                sql);
    }

}
