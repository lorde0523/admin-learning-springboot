package com.example.admin.common.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        LocalDateTime occurredAt) {
}

