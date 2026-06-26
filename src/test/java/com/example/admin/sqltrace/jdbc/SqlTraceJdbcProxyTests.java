package com.example.admin.sqltrace.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.admin.sqltrace.context.SqlCaptureContext;
import com.example.admin.sqltrace.context.SqlCaptureContextHolder;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SqlTraceJdbcProxyTests {

    private final CapturingStore store = new CapturingStore();
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource delegate = new DriverManagerDataSource(
                "jdbc:h2:mem:sql-trace-jdbc;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        SqlTraceRecorder recorder = new SqlTraceRecorder(
                new SqlParameterRenderer(new NoOpSqlValueMasker()),
                store);
        dataSource = new SqlTraceDataSource(delegate, recorder);

        try (Connection connection = delegate.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists test_menu");
            statement.execute("create table test_menu (id bigint primary key, name varchar(100))");
            statement.execute("insert into test_menu (id, name) values (1, 'Admin')");
        }

        SqlCaptureContextHolder.set(new SqlCaptureContext("user1", "request-1", "page01", false));
    }

    @AfterEach
    void clearContext() {
        SqlCaptureContextHolder.clear();
    }

    @Test
    void capturesSuccessfulExecuteQueryWithBoundValues() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id from test_menu where name = ?")) {
            statement.setString(1, "Admin");
            statement.executeQuery().close();
        }

        assertThat(store.entries).singleElement().satisfies(entry -> {
            assertThat(entry.sql()).contains("where name = 'Admin'");
            assertThat(entry.sqlElapsedMillis()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void capturesExecuteOnlyWhenItReturnsAResultSet() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("select id from test_menu");
             PreparedStatement update = connection.prepareStatement(
                     "update test_menu set name = 'Changed'")) {
            assertThat(select.execute()).isTrue();
            assertThat(update.execute()).isFalse();
        }

        assertThat(store.entries).hasSize(1);
        assertThat(store.entries.getFirst().sql()).startsWith("select");
    }

    @Test
    void doesNotCaptureFailedQueryOrUpdateMethods() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThatThrownBy(() -> {
                try (PreparedStatement broken =
                             connection.prepareStatement("select missing from test_menu")) {
                    broken.executeQuery();
                }
            }).isInstanceOf(SQLException.class);

            try (PreparedStatement update = connection.prepareStatement(
                    "update test_menu set name = ?")) {
                update.setString(1, "Changed");
                update.executeUpdate();
            }
        }

        assertThat(store.entries).isEmpty();
    }

    @Test
    void clearParametersRemovesPreviouslyBoundValues() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id from test_menu where name = ?")) {
            statement.setString(1, "Old");
            statement.clearParameters();
            statement.setString(1, "Admin");
            statement.executeQuery().close();
        }

        assertThat(store.entries).singleElement()
                .extracting(SqlTraceEntry::sql)
                .asString()
                .contains("name = 'Admin'")
                .doesNotContain("Old");
    }

    @Test
    void capturesSetNullAsSqlNullLiteral() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select ?")) {
            statement.setNull(1, java.sql.Types.VARCHAR);
            statement.executeQuery().close();
        }

        assertThat(store.entries).singleElement()
                .extracting(SqlTraceEntry::sql)
                .isEqualTo("select NULL");
    }

    @Test
    void proxiesHonorIdentityAndStatementConnectionStaysWrapped() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select 1")) {
            assertThat(connection.equals(connection)).isTrue();
            assertThat(connection.hashCode()).isEqualTo(System.identityHashCode(connection));
            assertThat(statement.equals(statement)).isTrue();
            assertThat(statement.hashCode()).isEqualTo(System.identityHashCode(statement));
            assertThat(statement.getConnection()).isSameAs(connection);
        }
    }

    @Test
    void closeDelegatesToAutoCloseableDataSource() throws Exception {
        CloseTrackingDataSource delegate = new CloseTrackingDataSource();
        SqlTraceRecorder recorder = new SqlTraceRecorder(
                new SqlParameterRenderer(new NoOpSqlValueMasker()),
                store);
        SqlTraceDataSource wrapper = new SqlTraceDataSource(delegate, recorder);

        wrapper.close();

        assertThat(delegate.closed).isTrue();
    }

    private static class CapturingStore implements SqlTraceStore {

        private final List<SqlTraceEntry> entries = new ArrayList<>();

        @Override
        public void append(SqlTraceEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<SqlTraceEntry> find(String username, String pageId) {
            return List.copyOf(entries);
        }

        @Override
        public boolean appendTimingIfOwned(SqlTraceEntry timing) {
            return false;
        }

        @Override
        public void delete(String username, String pageId) {
        }
    }

    private static class CloseTrackingDataSource
            extends org.springframework.jdbc.datasource.AbstractDataSource
            implements AutoCloseable {

        private boolean closed;

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:h2:mem:sql-trace-close", "sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection("jdbc:h2:mem:sql-trace-close", username, password);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
