package com.example.admin.sqltrace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SqlTraceTimingRequest(
        @NotBlank String requestId,
        @NotBlank String uiId,
        @NotNull @PositiveOrZero Double clientApiElapsedMillis,
        @NotNull @PositiveOrZero Double clientTotalElapsedMillis) {
}
