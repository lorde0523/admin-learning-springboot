package com.example.admin.common.sqltrace;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin.sql-log")
public class SqlTraceProperties {

    private Elasticsearch elasticsearch = new Elasticsearch();

    @Getter
    @Setter
    public static class Elasticsearch {
        private String url;
        private String indexPattern = "logs-*";
        private String apiKey;
        private String bearerToken;
        private long timeoutMillis = 1_000;
        private String requestTraceIdField = "requestTraceId";
        private String pageIdField = "pageId";
        private String sqlBatchIdField = "sqlBatchId";
        private String queryIdField = "queryId";
        private String sqlTextField = "sqlText";
        private String executedAtField = "executedAt";
        private String elapsedMillisField = "elapsedMillis";
    }
}
