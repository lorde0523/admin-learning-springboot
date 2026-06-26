package com.example.admin.sqltrace.storage;

import java.io.IOException;
import java.util.List;

public interface SqlTraceStore {

    void append(SqlTraceEntry entry) throws IOException;

    List<SqlTraceEntry> find(String username, String uiId) throws IOException;

    boolean appendTimingIfOwned(SqlTraceEntry timing) throws IOException;

    void delete(String username, String uiId) throws IOException;
}
