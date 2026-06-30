package com.example.admin.sqltrace.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

public record SqlTraceLogRow(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime apiStartedAt,
        String uiId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime executedAt,
        long sqlElapsedMillis,
        Long serverTimeMillis,
        Double clientTimeMillis,
        Double totalTimeMillis,
        String sql) {
}
