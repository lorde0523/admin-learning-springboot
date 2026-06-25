package com.example.admin.sqltrace.api;

import java.util.List;

public record SqlTraceLogResponse(
        String pageId,
        List<SqlTraceLogRow> logs) {
}
