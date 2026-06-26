package com.example.admin.sqltrace.api;

import com.example.admin.common.security.LoginUsers;
import com.example.admin.sqltrace.context.SqlTraceUserId;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
    private final Clock clock;

    public SqlTraceController(SqlTraceStore store, Clock sqlTraceClock) {
        this.store = store;
        this.clock = sqlTraceClock;
    }

    @GetMapping("/api/sql-logs")
    public SqlTraceLogResponse find(@RequestParam String uiId, HttpServletRequest servletRequest) {
        String username = currentUserId(servletRequest);
        String normalizedUiId = requireUiId(uiId);
        try {
            List<SqlTraceLogRow> rows = store.find(username, normalizedUiId).stream()
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
        String username = currentUserId(servletRequest);
        String requestId = requireRequestId(request.requestId());
        String uiId = requireUiId(request.uiId());
        if (!Double.isFinite(request.clientApiElapsedMillis())
                || !Double.isFinite(request.clientTotalElapsedMillis())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client elapsed times must be finite numbers.");
        }
        if (request.clientTotalElapsedMillis() < request.clientApiElapsedMillis()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "clientTotalElapsedMillis must be greater than or equal to clientApiElapsedMillis.");
        }
        try {
            boolean appended = store.appendTimingIfOwned(SqlTraceEntry.timing(
                    username,
                    requestId,
                    uiId,
                    OffsetDateTime.now(clock),
                    request.clientApiElapsedMillis(),
                    request.clientTotalElapsedMillis()));
            if (!appended) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SQL trace request was not found.");
            }
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw storageFailure("Failed to write SQL trace timing.", exception);
        }
    }

    @DeleteMapping("/api/sql-logs")
    public ResponseEntity<Void> clear(@RequestParam String uiId, HttpServletRequest servletRequest) {
        String username = currentUserId(servletRequest);
        String normalizedUiId = requireUiId(uiId);
        try {
            store.delete(username, normalizedUiId);
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw storageFailure("Failed to clear SQL trace logs.", exception);
        }
    }

    private String currentUserId(HttpServletRequest request) {
        String authenticatedUsername = LoginUsers.currentUsername()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication is required."));
        return SqlTraceUserId.resolve(request, authenticatedUsername);
    }

    private String requireRequestId(String requestId) {
        try {
            UUID parsed = UUID.fromString(requestId);
            if (!parsed.toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "requestId must be a UUID.",
                    exception);
        }
    }

    private String requireUiId(String uiId) {
        if (!StringUtils.hasText(uiId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uiId is required.");
        }
        return uiId.trim();
    }

    private ResponseStatusException storageFailure(String message, IOException exception) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                message,
                exception);
    }

    private SqlTraceLogRow toRow(SqlTraceEntry entry) {
        return new SqlTraceLogRow(
                entry.requestId(),
                entry.uiId(),
                entry.occurredAt(),
                entry.sqlElapsedMillis(),
                entry.clientApiElapsedMillis(),
                entry.clientTotalElapsedMillis(),
                entry.sql());
    }
}
