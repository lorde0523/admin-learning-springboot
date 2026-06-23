package com.example.admin.menu.service;

import com.example.admin.common.sqltrace.SqlLogEntry;
import com.example.admin.common.sqltrace.SqlLogSearchClient;
import com.example.admin.common.sqltrace.SqlLogSearchRequest;
import com.example.admin.common.sqltrace.SqlTracePageResponse;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.store.MyBatisAdminMenuStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MyBatisAdminMenuService {

    private static final Logger log = LoggerFactory.getLogger(MyBatisAdminMenuService.class);

    private final MyBatisAdminMenuStore menuStore;
    private final SqlLogSearchClient sqlLogSearchClient;

    public MyBatisAdminMenuService(MyBatisAdminMenuStore menuStore, SqlLogSearchClient sqlLogSearchClient) {
        this.menuStore = menuStore;
        this.sqlLogSearchClient = sqlLogSearchClient;
    }

    public SqlTracePageResponse<MenuResponse> searchPage(
            String nameKeyword,
            String pageId,
            boolean sqlCapturePaused,
            Pageable pageable) {
        if (!StringUtils.hasText(pageId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageId는 필수입니다.");
        }

        String requestTraceId = UUID.randomUUID().toString();
        String sqlBatchId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = LocalDateTime.now();
        long startedAt = System.nanoTime();

        setTraceContext(pageId, requestTraceId, sqlBatchId, sqlCapturePaused);
        try {
            List<MenuResponse> content = menuStore
                    .searchPage(nameKeyword, pageable.getPageSize(), pageable.getOffset())
                    .stream()
                    .map(MyBatisAdminMenuStore.MenuRow::toResponse)
                    .toList();
            long total = menuStore.countSearch(nameKeyword);
            Page<MenuResponse> page = new PageImpl<>(content, pageable, total);
            long serverElapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            List<SqlLogEntry> newSqlLogs = sqlCapturePaused
                    ? List.of()
                    : searchSqlLogs(requestTraceId, sqlBatchId, pageId, requestedAt, serverElapsedMillis);

            return SqlTracePageResponse.from(
                    page,
                    pageId,
                    sqlBatchId,
                    serverElapsedMillis,
                    sqlCapturePaused,
                    newSqlLogs);
        } finally {
            clearTraceContext();
        }
    }

    private List<SqlLogEntry> searchSqlLogs(
            String requestTraceId,
            String sqlBatchId,
            String pageId,
            LocalDateTime requestedAt,
            long serverElapsedMillis) {
        try {
            return sqlLogSearchClient.search(SqlLogSearchRequest.builder()
                    .requestTraceId(requestTraceId)
                    .sqlBatchId(sqlBatchId)
                    .pageId(pageId)
                    .requestedAt(requestedAt)
                    .serverElapsedMillis(serverElapsedMillis)
                    .build());
        } catch (RuntimeException exception) {
            log.warn("Failed to search SQL trace logs. pageId={} requestTraceId={}", pageId, requestTraceId, exception);
            return List.of();
        }
    }

    private void setTraceContext(String pageId, String requestTraceId, String sqlBatchId, boolean sqlCapturePaused) {
        MDC.put("pageId", pageId);
        MDC.put("requestTraceId", requestTraceId);
        MDC.put("sqlBatchId", sqlBatchId);
        MDC.put("sqlCapturePaused", Boolean.toString(sqlCapturePaused));
    }

    private void clearTraceContext() {
        MDC.remove("pageId");
        MDC.remove("requestTraceId");
        MDC.remove("sqlBatchId");
        MDC.remove("sqlCapturePaused");
    }
}
