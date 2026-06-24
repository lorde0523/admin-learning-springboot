package com.example.admin.sqltrace.config;

import com.example.admin.sqltrace.context.SqlCaptureRequestFilter;
import com.example.admin.sqltrace.jdbc.NoOpSqlValueMasker;
import com.example.admin.sqltrace.jdbc.SqlParameterRenderer;
import com.example.admin.sqltrace.jdbc.SqlTraceRecorder;
import com.example.admin.sqltrace.jdbc.SqlValueMasker;
import com.example.admin.sqltrace.storage.JsonLineSqlTraceStore;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SqlTraceProperties.class)
@ConditionalOnProperty(name = "admin.sql-trace.enabled", havingValue = "true")
public class SqlTraceConfiguration {

    @Bean
    public static SqlTraceDataSourceBeanPostProcessor sqlTraceDataSourceBeanPostProcessor(
            ObjectProvider<SqlTraceRecorder> recorderProvider) {
        return new SqlTraceDataSourceBeanPostProcessor(recorderProvider);
    }

    @Bean
    public Clock sqlTraceClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public SqlValueMasker sqlValueMasker() {
        return new NoOpSqlValueMasker();
    }

    @Bean
    public SqlParameterRenderer sqlParameterRenderer(SqlValueMasker masker) {
        return new SqlParameterRenderer(masker);
    }

    @Bean
    public SqlTraceStore sqlTraceStore(
            SqlTraceProperties properties,
            ObjectMapper objectMapper,
            Clock sqlTraceClock) {
        return new JsonLineSqlTraceStore(properties, objectMapper, sqlTraceClock);
    }

    @Bean
    public SqlTraceRecorder sqlTraceRecorder(
            SqlParameterRenderer renderer,
            SqlTraceStore store) {
        return new SqlTraceRecorder(renderer, store);
    }

    @Bean
    public SqlCaptureRequestFilter sqlCaptureRequestFilter() {
        return new SqlCaptureRequestFilter();
    }
}
