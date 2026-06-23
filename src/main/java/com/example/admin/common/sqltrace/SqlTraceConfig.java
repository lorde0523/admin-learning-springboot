package com.example.admin.common.sqltrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SqlTraceProperties.class)
public class SqlTraceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "admin.sql-log.elasticsearch", name = "url")
    public SqlLogSearchClient elasticsearchSqlLogSearchClient(
            SqlTraceProperties properties,
            ObjectMapper objectMapper) {
        return new ElasticsearchSqlLogSearchClient(properties.getElasticsearch(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SqlLogSearchClient.class)
    public SqlLogSearchClient sqlLogSearchClient() {
        return new NoOpSqlLogSearchClient();
    }
}
