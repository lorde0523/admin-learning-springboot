package com.example.admin.common.sqltrace;

import java.util.List;

public interface SqlLogSearchClient {

    List<SqlLogEntry> search(SqlLogSearchRequest request);
}
