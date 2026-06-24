package com.example.admin.sqltrace.jdbc;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

public class SqlTraceDataSource extends AbstractDataSource implements AutoCloseable {

    private final DataSource delegate;
    private final SqlTraceRecorder recorder;

    public SqlTraceDataSource(DataSource delegate, SqlTraceRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    public DataSource getDelegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() {
        try {
            return delegate.getLogWriter();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get DataSource log writer.", exception);
        }
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        try {
            delegate.setLogWriter(out);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to set DataSource log writer.", exception);
        }
    }

    @Override
    public void setLoginTimeout(int seconds) {
        try {
            delegate.setLoginTimeout(seconds);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to set DataSource login timeout.", exception);
        }
    }

    @Override
    public int getLoginTimeout() {
        try {
            return delegate.getLoginTimeout();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get DataSource login timeout.", exception);
        }
    }

    @Override
    public Logger getParentLogger() {
        try {
            return delegate.getParentLogger();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get DataSource parent logger.", exception);
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                new SqlTraceConnectionHandler(connection, recorder));
    }
}
