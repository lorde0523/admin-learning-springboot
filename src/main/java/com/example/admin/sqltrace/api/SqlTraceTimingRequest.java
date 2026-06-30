package com.example.admin.sqltrace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SqlTraceTimingRequest(
        @NotBlank String traceType,
        @NotBlank String apiStartedAt,
        @NotBlank String uiId,
        @NotNull @PositiveOrZero Double clientTimeMillis,
        @NotNull @PositiveOrZero Double totalTimeMillis) {
}
