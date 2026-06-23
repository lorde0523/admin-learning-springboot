package com.example.admin.common.sqltrace;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlLogEntry {

    private String queryId;
    private String sqlText;
    private LocalDateTime executedAt;
    private long elapsedMillis;
}
