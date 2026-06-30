package com.example.admin.sqltrace.storage;

import com.example.admin.sqltrace.context.SqlTraceType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

public interface SqlTraceStore {

    void append(SqlTraceEntry entry) throws IOException;

    List<SqlTraceEntry> find(SqlTraceType traceType, String userId, String uiId)
            throws IOException;

    long updateServerTiming(
            SqlTraceType traceType,
            String userId,
            String uiId,
            OffsetDateTime apiStartedAt,
            long serverTimeMillis) throws IOException;

    long updateClientTiming(
            SqlTraceType traceType,
            String userId,
            String uiId,
            OffsetDateTime apiStartedAt,
            double clientTimeMillis,
            double totalTimeMillis) throws IOException;

    void delete(SqlTraceType traceType, String userId, String uiId) throws IOException;
}
