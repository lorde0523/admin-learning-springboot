package com.example.admin.sqltrace.context;

public record SqlCaptureContext(
        String requestId,
        String pageId,
        boolean sqlCapturePaused) {
}
