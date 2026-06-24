package com.example.admin.sqltrace.storage;

import java.io.IOException;
import java.util.List;

public interface SqlTraceStore {

    void append(SqlTraceEntry entry) throws IOException;

    List<SqlTraceEntry> find(String requestId, String pageId) throws IOException;
}
