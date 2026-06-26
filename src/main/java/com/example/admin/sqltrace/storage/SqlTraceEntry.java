package com.example.admin.sqltrace.storage;

import java.time.OffsetDateTime;

public record SqlTraceEntry(
        SqlTraceEventType eventType,
        String username,
        String requestId,
        String uiId,
        OffsetDateTime occurredAt,
        Long sqlElapsedMillis,
        String sql,
        Double clientApiElapsedMillis,
        Double clientTotalElapsedMillis) {

    public static SqlTraceEntry query(
            String username,
            String requestId,
            String uiId,
            OffsetDateTime occurredAt,
            long sqlElapsedMillis,
            String sql) {
        return new SqlTraceEntry(
                SqlTraceEventType.QUERY,
                username,
                requestId,
                uiId,
                occurredAt,
                sqlElapsedMillis,
                sql,
                null,
                null);
    }

    public static SqlTraceEntry timing(
            String username,
            String requestId,
            String uiId,
            OffsetDateTime occurredAt,
            double clientApiElapsedMillis,
            double clientTotalElapsedMillis) {
        return new SqlTraceEntry(
                SqlTraceEventType.TIMING,
                username,
                requestId,
                uiId,
                occurredAt,
                null,
                null,
                clientApiElapsedMillis,
                clientTotalElapsedMillis);
    }

    public SqlTraceEntry withTiming(SqlTraceEntry timing) {
        if (timing == null) {
            return this;
        }
        return new SqlTraceEntry(
                eventType,
                username,
                requestId,
                uiId,
                occurredAt,
                sqlElapsedMillis,
                sql,
                timing.clientApiElapsedMillis,
                timing.clientTotalElapsedMillis);
    }
}
