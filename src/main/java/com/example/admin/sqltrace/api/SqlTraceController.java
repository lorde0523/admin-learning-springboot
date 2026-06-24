package com.example.admin.sqltrace.api;

import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam String requestId,
            @RequestParam String pageId) {
        validateRequestId(requestId);
        if (!StringUtils.hasText(pageId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageId is required.");
        }

        try {
            List<SqlTraceLogRow> rows = store.find(requestId, pageId.trim()).stream()
                    .map(this::toRow)
                    .toList();
            return new SqlTraceLogResponse(requestId, pageId.trim(), rows);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read SQL trace logs.",
                    exception);
        }
    }

    private void validateRequestId(String requestId) {
        try {
            UUID parsed = UUID.fromString(requestId);
            if (!parsed.toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "requestId must be a UUID.",
                    exception);
        }
    }

    private SqlTraceLogRow toRow(SqlTraceEntry entry) {
        return new SqlTraceLogRow(
                entry.pageId(),
                entry.executedAt(),
                entry.elapsedMillis(),
                entry.sql());
    }
}
