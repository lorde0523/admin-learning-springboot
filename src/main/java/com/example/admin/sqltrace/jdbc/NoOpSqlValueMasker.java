package com.example.admin.sqltrace.jdbc;

public class NoOpSqlValueMasker implements SqlValueMasker {

    @Override
    public Object mask(String sql, int parameterIndex, Object value) {
        // Replace NoOpSqlValueMasker when sensitive-value masking rules are defined.
        return value;
    }
}
