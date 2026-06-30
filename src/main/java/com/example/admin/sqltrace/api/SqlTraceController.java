package com.example.admin.sqltrace.api;

import com.example.admin.sqltrace.context.SqlTraceType;
import com.example.admin.sqltrace.context.SqlTraceUiId;
import com.example.admin.sqltrace.context.SqlTraceUserId;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "admin.sql-trace.enabled", havingValue = "true")
public class SqlTraceController {

    private final SqlTraceStore store;

    public SqlTraceController(SqlTraceStore store) {
        this.store = store;
    }

    @GetMapping("/api/sql-logs")
    public SqlTraceLogResponse find(
            @RequestParam String traceType,
            @RequestParam String uiId,
            HttpServletRequest servletRequest) {
        String userId = currentUserId(servletRequest);
        SqlTraceType normalizedTraceType = requireTraceType(traceType);
        String normalizedUiId = requireUiId(uiId);
        try {
            List<SqlTraceLogRow> rows = store.find(
                            normalizedTraceType,
                            userId,
                            normalizedUiId).stream()
                    .map(this::toRow)
                    .toList();
            return new SqlTraceLogResponse(normalizedUiId, rows);
        } catch (IOException exception) {
            throw storageFailure("Failed to read SQL trace logs.", exception);
        }
    }

    @PostMapping("/api/sql-logs/timing")
    public ResponseEntity<Void> appendTiming(
            @Valid @RequestBody SqlTraceTimingRequest request,
            HttpServletRequest servletRequest) {
        String userId = currentUserId(servletRequest);
        SqlTraceType traceType = requireTraceType(request.traceType());
        OffsetDateTime apiStartedAt = requireApiStartedAt(request.apiStartedAt());
        String uiId = requireUiId(request.uiId());
        if (!Double.isFinite(request.clientTimeMillis())
                || !Double.isFinite(request.totalTimeMillis())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client elapsed times must be finite numbers.");
        }
        if (request.totalTimeMillis() < request.clientTimeMillis()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "totalTimeMillis must be greater than or equal to clientTimeMillis.");
        }
        try {
            long updated = store.updateClientTiming(
                    traceType,
                    userId,
                    uiId,
                    apiStartedAt,
                    request.clientTimeMillis(),
                    request.totalTimeMillis());
            if (updated == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SQL trace API execution was not found.");
            }
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw storageFailure("Failed to write SQL trace timing.", exception);
        }
    }

    @DeleteMapping("/api/sql-logs")
    public ResponseEntity<Void> clear(
            @RequestParam String traceType,
            @RequestParam String uiId,
            HttpServletRequest servletRequest) {
        String userId = currentUserId(servletRequest);
        SqlTraceType normalizedTraceType = requireTraceType(traceType);
        String normalizedUiId = requireUiId(uiId);
        try {
            store.delete(normalizedTraceType, userId, normalizedUiId);
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw storageFailure("Failed to clear SQL trace logs.", exception);
        }
    }

    private String currentUserId(HttpServletRequest request) {
        return SqlTraceUserId.resolve(request)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication is required."));
    }

    private SqlTraceType requireTraceType(String traceType) {
        return SqlTraceType.from(traceType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "traceType must be query."));
    }

    private OffsetDateTime requireApiStartedAt(String apiStartedAt) {
        try {
            return OffsetDateTime.parse(apiStartedAt);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "apiStartedAt must be an ISO-8601 offset date-time.",
                    exception);
        }
    }

    private String requireUiId(String uiId) {
        return SqlTraceUiId.normalize(uiId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "uiId must use 1-100 letters, numbers, dots, underscores, or hyphens."));
    }

    private ResponseStatusException storageFailure(String message, IOException exception) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                message,
                exception);
    }

    private SqlTraceLogRow toRow(SqlTraceEntry entry) {
        return new SqlTraceLogRow(
                entry.apiStartedAt(),
                entry.uiId(),
                entry.occurredAt(),
                entry.sqlElapsedMillis(),
                entry.serverTimeMillis(),
                entry.clientTimeMillis(),
                entry.totalTimeMillis(),
                entry.sql());
    }
}
