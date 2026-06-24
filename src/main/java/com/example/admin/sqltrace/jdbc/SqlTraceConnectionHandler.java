package com.example.admin.sqltrace.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;

final class SqlTraceConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private final SqlTraceRecorder recorder;

    SqlTraceConnectionHandler(Connection delegate, SqlTraceRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
            return proxy == args[0];
        }
        if ("hashCode".equals(method.getName()) && (args == null || args.length == 0)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(method.getName()) && (args == null || args.length == 0)) {
            return "SqlTraceConnection[" + delegate + "]";
        }
        if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
            Class<?> type = (Class<?>) args[0];
            if (type.isInstance(proxy)) {
                return proxy;
            }
        }
        if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1) {
            Class<?> type = (Class<?>) args[0];
            if (type.isInstance(proxy)) {
                return true;
            }
        }

        Object result = invokeDelegate(method, args);
        if ("prepareStatement".equals(method.getName())
                && args != null
                && args.length > 0
                && args[0] instanceof String sql
                && result instanceof PreparedStatement statement) {
            return Proxy.newProxyInstance(
                    statement.getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    new SqlTracePreparedStatementHandler(
                            statement,
                            (Connection) proxy,
                            sql,
                            recorder));
        }
        return result;
    }

    private Object invokeDelegate(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
