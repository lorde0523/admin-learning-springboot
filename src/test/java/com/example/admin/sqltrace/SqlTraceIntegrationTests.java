package com.example.admin.sqltrace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.common.security.LoginUser;
import com.example.admin.common.redis.RedisKey;
import com.example.admin.common.redis.RedisStore;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.repository.AdminMenuRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SqlTraceIntegrationTests {

    private static final AtomicLong IDS = new AtomicLong(900_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminMenuRepository menuRepository;

    @Autowired
    private InMemoryRedisStore redisStore;

    private String menuName;

    @BeforeEach
    void setUp() throws Exception {
        redisStore.clear();
        long id = IDS.incrementAndGet();
        menuName = "Trace Paging " + id;
        menuRepository.save(AdminMenu.create(
                id,
                "TRACE_" + id,
                menuName,
                null,
                1,
                true));
    }

    @Test
    void accumulatesParameterCompleteMyBatisSelectsForThePage() throws Exception {
        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);
        int firstCount = listLogs("user1", "page01").size();

        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);
        JsonNode accumulated = listLogs("user1", "page01");

        assertThat(firstCount).isPositive();
        assertThat(accumulated.size()).isGreaterThan(firstCount);
        assertThat(accumulated.toString()).doesNotContain("?");
    }

    @Test
    void capturesJpaSelectsThroughTheSameUserScopedList() throws Exception {
        trackedGet("/api/jpa/menus/page", "user1", "page02", false);

        JsonNode logs = listLogs("user1", "page02");

        assertThat(logs).isNotEmpty();
        assertThat(logs.toString().toLowerCase()).contains("admin_menu");
        assertThat(logs.toString()).doesNotContain("?");
    }

    @Test
    void doesNotExposeOneUsersLogsToAnotherUser() throws Exception {
        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);

        assertThat(listLogs("user1", "page01")).isNotEmpty();
        assertThat(listLogs("user2", "page01")).isEmpty();
    }

    @Test
    void pausedRequestKeepsPreviousListWithoutAppending() throws Exception {
        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);
        int beforePause = listLogs("user1", "page01").size();

        trackedGet("/api/mybatis/menus/page", "user1", "page01", true);

        assertThat(listLogs("user1", "page01")).hasSize(beforePause);
    }

    @Test
    void clearHidesPreviousRowsAndLaterQueriesAppearAgain() throws Exception {
        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);
        assertThat(listLogs("user1", "page01")).isNotEmpty();

        mockMvc.perform(delete("/api/sql-logs")
                        .with(login("user1"))
                        .param("uiId", "page01"))
                .andExpect(status().isNoContent());
        assertThat(listLogs("user1", "page01")).isEmpty();

        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);
        assertThat(listLogs("user1", "page01")).isNotEmpty();
    }

    @Test
    void clientTimingIsCombinedWithEverySqlFromTheBusinessRequest() throws Exception {
        MvcResult result = trackedGet(
                "/api/mybatis/menus/page", "user1", "page01", false);
        String requestId = result.getResponse().getHeader("X-Request-Id");

        mockMvc.perform(post("/api/sql-logs/timing")
                        .with(login("user1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientApiElapsedMillis": 35.2,
                                  "clientTotalElapsedMillis": 48.7
                                }
                                """.formatted(requestId)))
                .andExpect(status().isNoContent());

        JsonNode logs = listLogs("user1", "page01");
        for (JsonNode log : logs) {
            if (requestId.equals(log.get("requestId").asText())) {
                assertThat(log.get("clientApiElapsedMillis").asDouble()).isEqualTo(35.2);
                assertThat(log.get("clientTotalElapsedMillis").asDouble()).isEqualTo(48.7);
            }
        }
    }

    @Test
    void unauthenticatedAndUnidentifiedRequestsAreNotTraced() throws Exception {
        mockMvc.perform(get("/api/mybatis/menus/page")
                        .header("X-Ui-Id", "page01")
                        .header("X-Sql-Capture-Paused", "false")
                        .param("nameKeyword", menuName)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));

        mockMvc.perform(get("/api/sql-logs")
                        .param("uiId", "page01"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sql-logs/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restoresLoginUserFromSessionForNexacroStyleFollowUpRequests() throws Exception {
        LoginUser loginUser = LoginUser.sessionUser("session-user", "Session User", List.of());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        loginUser,
                        "",
                        loginUser.getAuthorities())));

        MvcResult business = mockMvc.perform(get("/api/mybatis/menus/page")
                        .session(session)
                        .header("X-Ui-Id", "page01")
                        .header("X-Sql-Capture-Paused", "false")
                        .param("nameKeyword", menuName)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String requestId = business.getResponse().getHeader("X-Request-Id");
        mockMvc.perform(post("/api/sql-logs/timing")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "uiId": "page01",
                                  "clientApiElapsedMillis": 10,
                                  "clientTotalElapsedMillis": 20
                                }
                                """.formatted(requestId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sql-logs")
                        .session(session)
                        .param("uiId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isNotEmpty())
                .andExpect(jsonPath("$.logs[0].clientApiElapsedMillis").value(10));

        mockMvc.perform(delete("/api/sql-logs")
                        .session(session)
                        .param("uiId", "page01"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/sql-logs")
                        .session(session)
                        .param("uiId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs", hasSize(0)));
    }

    @Test
    void sqlLogEndpointsDoNotCreateAnotherTrace() throws Exception {
        trackedGet("/api/mybatis/menus/page", "user1", "page01", false);

        mockMvc.perform(get("/api/sql-logs")
                        .with(login("user1"))
                        .header("X-Ui-Id", "page01")
                        .header("X-Sql-Capture-Paused", "false")
                        .param("uiId", "page01"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));
    }

    private MvcResult trackedGet(
            String path,
            String username,
            String pageId,
            boolean paused) throws Exception {
        return mockMvc.perform(get(path)
                        .with(login(username))
                        .header("X-Ui-Id", pageId)
                        .header("X-Sql-Capture-Paused", Boolean.toString(paused))
                        .param("nameKeyword", menuName)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].menuName", is(menuName)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn();
    }

    private JsonNode listLogs(String username, String pageId) throws Exception {
        String response = mockMvc.perform(get("/api/sql-logs")
                        .with(login(username))
                        .param("uiId", pageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[*].sql", not(containsString("?"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("logs");
    }

    private RequestPostProcessor login(String username) {
        LoginUser loginUser = LoginUser.sessionUser(username, username, List.of());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginUser,
                "",
                loginUser.getAuthorities()));
    }

    @TestConfiguration
    static class RedisTestConfiguration {

        @Bean
        @Primary
        InMemoryRedisStore inMemoryRedisStore() {
            return new InMemoryRedisStore();
        }
    }

    static class InMemoryRedisStore implements RedisStore {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(RedisKey key) throws IOException {
            return Optional.ofNullable(values.get(key.value()));
        }

        @Override
        public void save(RedisKey key, String value) throws IOException {
            values.put(key.value(), value);
        }

        @Override
        public void delete(RedisKey key) throws IOException {
            values.remove(key.value());
        }

        void clear() {
            values.clear();
        }
    }
}
