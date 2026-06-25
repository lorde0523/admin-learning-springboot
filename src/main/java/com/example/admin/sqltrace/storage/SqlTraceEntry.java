package com.example.admin.sqltrace.storage;

import java.time.OffsetDateTime;

public record SqlTraceEntry(
        SqlTraceEventType eventType,
        String username,
        String requestId,
        String pageId,
        OffsetDateTime occurredAt,
        Long sqlElapsedMillis,
        String sql,
        Double clientApiElapsedMillis,
        Double clientTotalElapsedMillis) {

    public static SqlTraceEntry query(
            String username,
            String requestId,
            String pageId,
            OffsetDateTime occurredAt,
            long sqlElapsedMillis,
            String sql) {
        return new SqlTraceEntry(
                SqlTraceEventType.QUERY,
                username,
                requestId,
                pageId,
                occurredAt,
                sqlElapsedMillis,
                sql,
                null,
                null);
    }

    public static SqlTraceEntry timing(
            String username,
            String requestId,
            String pageId,
            OffsetDateTime occurredAt,
            double clientApiElapsedMillis,
            double clientTotalElapsedMillis) {
        return new SqlTraceEntry(
                SqlTraceEventType.TIMING,
                username,
                requestId,
                pageId,
                occurredAt,
                null,
                null,
                clientApiElapsedMillis,
                clientTotalElapsedMillis);
    }

    public static SqlTraceEntry clear(
            String username,
            String pageId,
            OffsetDateTime occurredAt) {
        return new SqlTraceEntry(
                SqlTraceEventType.CLEAR,
                username,
                null,
                pageId,
                occurredAt,
                null,
                null,
                null,
                null);
    }

    public SqlTraceEntry withTiming(SqlTraceEntry timing) {
        if (timing == null) {
            return this;
        }
        return new SqlTraceEntry(
                eventType,
                username,
                requestId,
                pageId,
                occurredAt,
                sqlElapsedMillis,
                sql,
                timing.clientApiElapsedMillis,
                timing.clientTotalElapsedMillis);
    }
}
