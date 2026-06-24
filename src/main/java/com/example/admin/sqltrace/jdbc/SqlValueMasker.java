package com.example.admin.sqltrace.jdbc;

@FunctionalInterface
public interface SqlValueMasker {

    Object mask(String sql, int parameterIndex, Object value);
}
