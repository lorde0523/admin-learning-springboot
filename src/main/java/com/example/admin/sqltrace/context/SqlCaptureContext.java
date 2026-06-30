package com.example.admin.sqltrace.context;

import java.time.OffsetDateTime;

public record SqlCaptureContext(
        SqlTraceType traceType,
        String userId,
        OffsetDateTime apiStartedAt,
        String uiId,
        boolean sqlCapturePaused) {
}
