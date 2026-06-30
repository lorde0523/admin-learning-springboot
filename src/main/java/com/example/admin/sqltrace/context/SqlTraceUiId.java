package com.example.admin.sqltrace.context;

import java.util.Optional;
import java.util.regex.Pattern;

public final class SqlTraceUiId {

    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private SqlTraceUiId() {
    }

    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return VALID_VALUE.matcher(normalized).matches()
                ? Optional.of(normalized)
                : Optional.empty();
    }
}
