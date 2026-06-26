package com.example.admin.sqltrace.context;

public record SqlCaptureContext(
        String username,
        String requestId,
        String uiId,
        boolean sqlCapturePaused) {
}
