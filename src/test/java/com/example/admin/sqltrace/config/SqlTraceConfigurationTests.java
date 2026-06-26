package com.example.admin.sqltrace.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.sqltrace.context.SqlCaptureRequestFilter;
import com.example.admin.sqltrace.jdbc.SqlTraceDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SqlTraceConfigurationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlCaptureRequestFilter filter;

    @Test
    void wrapsApplicationDataSourceExactlyOnce() {
        assertThat(dataSource).isInstanceOf(SqlTraceDataSource.class);
        assertThat(((SqlTraceDataSource) dataSource).getDelegate())
                .isNotInstanceOf(SqlTraceDataSource.class);
    }

    @Test
    void registersFilter() {
        assertThat(filter).isNotNull();
    }
}
