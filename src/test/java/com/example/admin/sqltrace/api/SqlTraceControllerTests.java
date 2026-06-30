package com.example.admin.sqltrace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.common.exception.ApiExceptionHandler;
import com.example.admin.common.security.LoginUser;
import com.example.admin.sqltrace.context.SqlTraceType;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SqlTraceControllerTests {

    private static final OffsetDateTime API_STARTED_AT =
            OffsetDateTime.parse("2026-07-01T10:30:15.100+09:00");
    private static final OffsetDateTime EXECUTED_AT =
            OffsetDateTime.parse("2026-07-01T10:30:15.200+09:00");

    private final StubStore store = new StubStore();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = LoginUser.sessionUser("user1", "User 1", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        loginUser,
                        "",
                        loginUser.getAuthorities()));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SqlTraceController(store))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsNewestSqlObjectsWithServerAndClientTiming() throws Exception {
        store.entries = List.of(query("select 2", 7), query("select 1", 4));

        mockMvc.perform(get("/api/sql-logs")
                        .param("traceType", "query")
                        .param("uiId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiId", is("page01")))
                .andExpect(jsonPath("$.logs", hasSize(2)))
                .andExpect(jsonPath("$.logs[0].apiStartedAt",
                        is("2026-07-01T10:30:15.1+09:00")))
                .andExpect(jsonPath("$.logs[0].sql", is("select 2")))
                .andExpect(jsonPath("$.logs[0].sqlElapsedMillis").value(7))
                .andExpect(jsonPath("$.logs[0].serverTimeMillis").value(500))
                .andExpect(jsonPath("$.logs[0].clientTimeMillis").value(300))
                .andExpect(jsonPath("$.logs[0].totalTimeMillis").value(1000))
                .andExpect(jsonPath("$.logs[0].requestId").doesNotExist());

        assertThat(store.lastFindTraceType).isEqualTo(SqlTraceType.QUERY);
        assertThat(store.lastFindUserId).isEqualTo("user1");
    }

    @Test
    void updatesEverySqlObjectMatchingApiStartedAt() throws Exception {
        store.clientTimingUpdateCount = 2;

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "traceType": "query",
                                  "apiStartedAt": "2026-07-01T10:30:15.100+09:00",
                                  "uiId": "page01",
                                  "clientTimeMillis": 300,
                                  "totalTimeMillis": 1000
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(store.lastTimingTraceType).isEqualTo(SqlTraceType.QUERY);
        assertThat(store.lastTimingUserId).isEqualTo("user1");
        assertThat(store.lastTimingUiId).isEqualTo("page01");
        assertThat(store.lastApiStartedAt).isEqualTo(API_STARTED_AT);
        assertThat(store.lastClientTimeMillis).isEqualTo(300);
        assertThat(store.lastTotalTimeMillis).isEqualTo(1000);
    }

    @Test
    void returnsNotFoundWhenNoSqlMatchesApiStartedAt() throws Exception {
        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "traceType": "query",
                                  "apiStartedAt": "2026-07-01T10:30:15.100+09:00",
                                  "uiId": "page01",
                                  "clientTimeMillis": 300,
                                  "totalTimeMillis": 1000
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidStartedAtOrTimingValues() throws Exception {
        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "traceType": "query",
                                  "apiStartedAt": "invalid",
                                  "uiId": "page01",
                                  "clientTimeMillis": 300,
                                  "totalTimeMillis": 1000
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "traceType": "query",
                                  "apiStartedAt": "2026-07-01T10:30:15.100+09:00",
                                  "uiId": "page01",
                                  "clientTimeMillis": 1100,
                                  "totalTimeMillis": 1000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidTraceTypeOrUiId() throws Exception {
        mockMvc.perform(get("/api/sql-logs")
                        .param("traceType", "unknown")
                        .param("uiId", "page01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sql-logs")
                        .param("traceType", "query")
                        .param("uiId", "page:01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearDeletesCurrentUsersTypedScreenList() throws Exception {
        mockMvc.perform(delete("/api/sql-logs")
                        .param("traceType", "query")
                        .param("uiId", "page01"))
                .andExpect(status().isNoContent());

        assertThat(store.deletedTraceType).isEqualTo(SqlTraceType.QUERY);
        assertThat(store.deletedUserId).isEqualTo("user1");
        assertThat(store.deletedUiId).isEqualTo("page01");
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/sql-logs")
                        .param("traceType", "query")
                        .param("uiId", "page01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsServerErrorWhenRedisReadFails() throws Exception {
        store.failure = new IOException("read failed");

        mockMvc.perform(get("/api/sql-logs")
                        .param("traceType", "query")
                        .param("uiId", "page01"))
                .andExpect(status().isInternalServerError());
    }

    private SqlTraceEntry query(String sql, long sqlElapsedMillis) {
        return new SqlTraceEntry(
                SqlTraceType.QUERY,
                "user1",
                API_STARTED_AT,
                "page01",
                EXECUTED_AT,
                sqlElapsedMillis,
                500L,
                300.0,
                1000.0,
                sql);
    }

    private static class StubStore implements SqlTraceStore {

        private List<SqlTraceEntry> entries = List.of();
        private IOException failure;
        private long clientTimingUpdateCount;
        private SqlTraceType lastFindTraceType;
        private String lastFindUserId;
        private SqlTraceType lastTimingTraceType;
        private String lastTimingUserId;
        private String lastTimingUiId;
        private OffsetDateTime lastApiStartedAt;
        private double lastClientTimeMillis;
        private double lastTotalTimeMillis;
        private SqlTraceType deletedTraceType;
        private String deletedUserId;
        private String deletedUiId;

        @Override
        public void append(SqlTraceEntry entry) {
        }

        @Override
        public List<SqlTraceEntry> find(
                SqlTraceType traceType,
                String userId,
                String uiId) throws IOException {
            throwIfFailed();
            lastFindTraceType = traceType;
            lastFindUserId = userId;
            return entries;
        }

        @Override
        public long updateServerTiming(
                SqlTraceType traceType,
                String userId,
                String uiId,
                OffsetDateTime apiStartedAt,
                long serverTimeMillis) {
            return 0;
        }

        @Override
        public long updateClientTiming(
                SqlTraceType traceType,
                String userId,
                String uiId,
                OffsetDateTime apiStartedAt,
                double clientTimeMillis,
                double totalTimeMillis) throws IOException {
            throwIfFailed();
            lastTimingTraceType = traceType;
            lastTimingUserId = userId;
            lastTimingUiId = uiId;
            lastApiStartedAt = apiStartedAt;
            lastClientTimeMillis = clientTimeMillis;
            lastTotalTimeMillis = totalTimeMillis;
            return clientTimingUpdateCount;
        }

        @Override
        public void delete(SqlTraceType traceType, String userId, String uiId)
                throws IOException {
            throwIfFailed();
            deletedTraceType = traceType;
            deletedUserId = userId;
            deletedUiId = uiId;
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
