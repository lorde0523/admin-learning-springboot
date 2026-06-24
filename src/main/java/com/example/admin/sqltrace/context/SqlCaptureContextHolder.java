package com.example.admin.sqltrace.context;

import java.util.Optional;

public final class SqlCaptureContextHolder {

    private static final ThreadLocal<SqlCaptureContext> CURRENT = new ThreadLocal<>();

    private SqlCaptureContextHolder() {
    }

    public static Optional<SqlCaptureContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(SqlCaptureContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
