package com.example.admin.common.sqltrace;

import java.util.List;

public class NoOpSqlLogSearchClient implements SqlLogSearchClient {

    @Override
    public List<SqlLogEntry> search(SqlLogSearchRequest request) {
        return List.of();
    }
}
