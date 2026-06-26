package com.example.admin.sqltrace.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.common.exception.ApiExceptionHandler;
import com.example.admin.common.security.LoginUser;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceEventType;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SqlTraceControllerTests {

    private static final String REQUEST_ID = "2de4a7d7-1453-4652-85dd-ef8ddfa57467";
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-06-25T14:20:31+09:00");

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
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T05:20:31Z"), ZoneId.of("Asia/Seoul"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SqlTraceController(store, clock))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAccumulatedRowsForCurrentUserAndPage() throws Exception {
        store.entries = List.of(query());

        mockMvc.perform(get("/api/sql-logs")
                        .cookie(new MockCookie("LASTUSER", "last-user"))
                        .param("uiId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiId", is("page01")))
                .andExpect(jsonPath("$.logs", hasSize(1)))
                .andExpect(jsonPath("$.logs[0].requestId", is(REQUEST_ID)))
                .andExpect(jsonPath("$.logs[0].uiId", is("page01")))
                .andExpect(jsonPath("$.logs[0].sql", is("select 1")))
                .andExpect(jsonPath("$.logs[0].sqlElapsedMillis").value(4))
                .andExpect(jsonPath("$.logs[0].clientApiElapsedMillis").value(35.2))
                .andExpect(jsonPath("$.logs[0].clientTotalElapsedMillis").value(48.7));

        assertThat(store.lastFindUsername).isEqualTo("last-user");
        assertThat(store.lastFindPageId).isEqualTo("page01");
    }

    @Test
    void rejectsUnauthenticatedListRequest() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/sql-logs").param("uiId", "page01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fallsBackToAuthenticatedUsernameWithoutLastUserCookie() throws Exception {
        store.entries = List.of(query());

        mockMvc.perform(get("/api/sql-logs").param("uiId", "page01"))
                .andExpect(status().isOk());

        assertThat(store.lastFindUsername).isEqualTo("user1");
    }

    @Test
    void appendsTimingOnlyForOwnedQuery() throws Exception {
        store.queryExists = true;

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SqlTraceTimingRequest(
                                REQUEST_ID,
                                "page01",
                                35.2,
                                48.7))))
                .andExpect(status().isNoContent());

        assertThat(store.appended).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo(SqlTraceEventType.TIMING);
            assertThat(entry.username()).isEqualTo("user1");
            assertThat(entry.requestId()).isEqualTo(REQUEST_ID);
        });
    }

    @Test
    void rejectsTimingForAnotherUsersQuery() throws Exception {
        store.queryExists = false;

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SqlTraceTimingRequest(
                                REQUEST_ID,
                                "page01",
                                35.2,
                                48.7))))
                .andExpect(status().isNotFound());

        assertThat(store.appended).isEmpty();
    }

    @Test
    void rejectsMissingOrInconsistentTimingValues() throws Exception {
        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientTotalElapsedMillis": 48.7
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientApiElapsedMillis": 50.0,
                                  "clientTotalElapsedMillis": 40.0
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonFiniteTimingValues() throws Exception {
        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientApiElapsedMillis": "NaN",
                                  "clientTotalElapsedMillis": 48.7
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientApiElapsedMillis": 35.2,
                                  "clientTotalElapsedMillis": "Infinity"
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearDeletesUserScopedLogs() throws Exception {
        mockMvc.perform(delete("/api/sql-logs").param("uiId", "page01"))
                .andExpect(status().isNoContent());

        assertThat(store.deletedUsername).isEqualTo("user1");
        assertThat(store.deletedPageId).isEqualTo("page01");
        assertThat(store.appended).isEmpty();
    }

    @Test
    void returnsServerErrorWhenFileReadFails() throws Exception {
        store.failure = new IOException("read failed");

        mockMvc.perform(get("/api/sql-logs").param("uiId", "page01"))
                .andExpect(status().isInternalServerError());
    }

    private SqlTraceEntry query() {
        return new SqlTraceEntry(
                SqlTraceEventType.QUERY,
                "user1",
                REQUEST_ID,
                "page01",
                NOW,
                4L,
                "select 1",
                35.2,
                48.7);
    }

    private static class StubStore implements SqlTraceStore {

        private List<SqlTraceEntry> entries = List.of();
        private final List<SqlTraceEntry> appended = new ArrayList<>();
        private IOException failure;
        private boolean queryExists;
        private String lastFindUsername;
        private String lastFindPageId;
        private String deletedUsername;
        private String deletedPageId;

        @Override
        public void append(SqlTraceEntry entry) throws IOException {
            if (failure != null) {
                throw failure;
            }
            appended.add(entry);
        }

        @Override
        public List<SqlTraceEntry> find(String username, String pageId) throws IOException {
            if (failure != null) {
                throw failure;
            }
            lastFindUsername = username;
            lastFindPageId = pageId;
            return entries;
        }

        @Override
        public boolean appendTimingIfOwned(SqlTraceEntry timing) throws IOException {
            if (failure != null) {
                throw failure;
            }
            if (!queryExists) {
                return false;
            }
            appended.add(timing);
            return true;
        }

        @Override
        public void delete(String username, String pageId) throws IOException {
            if (failure != null) {
                throw failure;
            }
            deletedUsername = username;
            deletedPageId = pageId;
        }
    }
}
