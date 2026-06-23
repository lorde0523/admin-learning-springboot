package com.example.admin.common.sqltrace;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@Getter
@Builder
public class SqlTracePageResponse<T> {

    private String pageId;
    private String sqlBatchId;
    private long serverElapsedMillis;
    private boolean sqlCapturePaused;
    private List<SqlLogEntry> newSqlLogs;
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private int size;
    private int number;
    private Sort sort;
    private int numberOfElements;
    private boolean first;
    private boolean empty;

    public static <T> SqlTracePageResponse<T> from(
            Page<T> page,
            String pageId,
            String sqlBatchId,
            long serverElapsedMillis,
            boolean sqlCapturePaused,
            List<SqlLogEntry> newSqlLogs) {
        return SqlTracePageResponse.<T>builder()
                .pageId(pageId)
                .sqlBatchId(sqlBatchId)
                .serverElapsedMillis(serverElapsedMillis)
                .sqlCapturePaused(sqlCapturePaused)
                .newSqlLogs(newSqlLogs == null ? List.of() : newSqlLogs)
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .size(page.getSize())
                .number(page.getNumber())
                .sort(page.getSort())
                .numberOfElements(page.getNumberOfElements())
                .first(page.isFirst())
                .empty(page.isEmpty())
                .build();
    }
}
