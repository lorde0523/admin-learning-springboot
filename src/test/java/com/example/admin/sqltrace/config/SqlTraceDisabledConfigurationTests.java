package com.example.admin.sqltrace.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.sqltrace.api.SqlTraceController;
import com.example.admin.sqltrace.context.SqlCaptureRequestFilter;
import com.example.admin.sqltrace.jdbc.SqlTraceDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "admin.sql-trace.enabled=false")
class SqlTraceDisabledConfigurationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void doesNotRegisterTracingInfrastructureWhenDisabled() {
        assertThat(applicationContext.getBeansOfType(SqlTraceController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SqlCaptureRequestFilter.class)).isEmpty();
        assertThat(dataSource).isNotInstanceOf(SqlTraceDataSource.class);
    }
}
