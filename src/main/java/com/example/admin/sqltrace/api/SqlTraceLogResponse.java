package com.example.admin.sqltrace.api;

import java.util.List;

public record SqlTraceLogResponse(
        String uiId,
        List<SqlTraceLogRow> logs) {
}
