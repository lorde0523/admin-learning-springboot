package com.example.admin.sqltrace.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

final class SqlTracePreparedStatementHandler implements InvocationHandler {

    private final PreparedStatement delegate;
    private final Connection connection;
    private final String sql;
    private final SqlTraceRecorder recorder;
    private final Map<Integer, Object> parameters = new HashMap<>();

    SqlTracePreparedStatementHandler(
            PreparedStatement delegate,
            Connection connection,
            String sql,
            SqlTraceRecorder recorder) {
        this.delegate = delegate;
        this.connection = connection;
        this.sql = sql;
        this.recorder = recorder;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if ("equals".equals(methodName) && args != null && args.length == 1) {
            return proxy == args[0];
        }
        if ("hashCode".equals(methodName) && (args == null || args.length == 0)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(methodName) && (args == null || args.length == 0)) {
            return "SqlTracePreparedStatement[" + delegate + "]";
        }
        if ("getConnection".equals(methodName) && (args == null || args.length == 0)) {
            return connection;
        }
        if ("unwrap".equals(methodName) && args != null && args.length == 1) {
            Class<?> type = (Class<?>) args[0];
            if (type.isInstance(proxy)) {
                return proxy;
            }
        }
        if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
            Class<?> type = (Class<?>) args[0];
            if (type.isInstance(proxy)) {
                return true;
            }
        }

        if ("executeQuery".equals(methodName) && hasNoArguments(args)) {
            return executeAndRecord(method, args, true);
        }
        if ("execute".equals(methodName) && hasNoArguments(args)) {
            long startedAt = System.nanoTime();
            Object result = invokeDelegate(method, args);
            long elapsedNanos = System.nanoTime() - startedAt;
            if (Boolean.TRUE.equals(result)) {
                recorder.record(sql, new HashMap<>(parameters), elapsedNanos, OffsetDateTime.now());
            }
            return result;
        }

        Object result = invokeDelegate(method, args);
        if ("clearParameters".equals(methodName)) {
            parameters.clear();
        } else if (isBindingMethod(methodName, args)) {
            int parameterIndex = (Integer) args[0];
            Object value = "setNull".equals(methodName) ? null : args[1];
            parameters.put(parameterIndex, value);
        }
        return result;
    }

    private Object executeAndRecord(Method method, Object[] args, boolean record) throws Throwable {
        long startedAt = System.nanoTime();
        Object result = invokeDelegate(method, args);
        long elapsedNanos = System.nanoTime() - startedAt;
        if (record) {
            recorder.record(sql, new HashMap<>(parameters), elapsedNanos, OffsetDateTime.now());
        }
        return result;
    }

    private boolean hasNoArguments(Object[] args) {
        return args == null || args.length == 0;
    }

    private boolean isBindingMethod(String methodName, Object[] args) {
        return methodName.startsWith("set")
                && args != null
                && args.length >= 2
                && args[0] instanceof Integer;
    }

    private Object invokeDelegate(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
