package com.example.admin.sqltrace.storage;

import java.io.IOException;
import java.util.List;

public interface SqlTraceStore {

    void append(SqlTraceEntry entry) throws IOException;

    List<SqlTraceEntry> find(String username, String pageId) throws IOException;

    boolean appendTimingIfOwned(SqlTraceEntry timing) throws IOException;
}
