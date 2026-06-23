package com.example.admin.common.sqltrace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class ElasticsearchSqlLogSearchClient implements SqlLogSearchClient {

    private final SqlTraceProperties.Elasticsearch properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ElasticsearchSqlLogSearchClient(
            SqlTraceProperties.Elasticsearch properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .build();
    }

    @Override
    public List<SqlLogEntry> search(SqlLogSearchRequest request) {
        try {
            HttpRequest httpRequest = request(request);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Elasticsearch SQL log search failed. status=" + response.statusCode());
            }
            return entries(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch SQL log search failed.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Elasticsearch SQL log search was interrupted.", exception);
        }
    }

    private HttpRequest request(SqlLogSearchRequest request) throws IOException {
        String body = objectMapper.writeValueAsString(Map.of(
                "size", 50,
                "sort", List.of(Map.of(properties.getExecutedAtField(), Map.of("order", "asc"))),
                "query", Map.of(
                        "bool", Map.of(
                                "filter", List.of(
                                        term(properties.getRequestTraceIdField(), request.getRequestTraceId()),
                                        term(properties.getPageIdField(), request.getPageId()),
                                        term(properties.getSqlBatchIdField(), request.getSqlBatchId()))))));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchUrl()))
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.header("Authorization", "ApiKey " + properties.getApiKey());
        } else if (StringUtils.hasText(properties.getBearerToken())) {
            builder.header("Authorization", "Bearer " + properties.getBearerToken());
        }
        return builder.build();
    }

    private Map<String, Map<String, String>> term(String fieldName, String value) {
        return Map.of("term", Map.of(fieldName, value));
    }

    private String elasticsearchUrl() {
        String baseUrl = properties.getUrl().endsWith("/")
                ? properties.getUrl().substring(0, properties.getUrl().length() - 1)
                : properties.getUrl();
        return baseUrl + "/" + properties.getIndexPattern() + "/_search";
    }

    private List<SqlLogEntry> entries(String responseBody) throws IOException {
        JsonNode hits = objectMapper.readTree(responseBody).path("hits").path("hits");
        List<SqlLogEntry> entries = new ArrayList<>();
        if (!hits.isArray()) {
            return entries;
        }
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            entries.add(SqlLogEntry.builder()
                    .queryId(text(source, properties.getQueryIdField()))
                    .sqlText(text(source, properties.getSqlTextField()))
                    .executedAt(executedAt(source))
                    .elapsedMillis(source.path(properties.getElapsedMillisField()).asLong())
                    .build());
        }
        return entries;
    }

    private String text(JsonNode source, String fieldName) {
        JsonNode value = source.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private LocalDateTime executedAt(JsonNode source) {
        String value = text(source, properties.getExecutedAtField());
        return StringUtils.hasText(value) ? LocalDateTime.parse(value) : null;
    }
}
