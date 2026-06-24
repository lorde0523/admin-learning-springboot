package com.example.admin.sqltrace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.repository.AdminMenuRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SqlTraceIntegrationTests {

    private static final AtomicLong IDS = new AtomicLong(900_000L);
    private static final Path LOG_DIRECTORY = Path.of("./build/test-sql-trace");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminMenuRepository menuRepository;

    private String menuName;

    @BeforeEach
    void setUp() throws Exception {
        deleteLogDirectory();
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
    void capturesParameterCompleteMyBatisSelectsWithoutChangingPagingResponse() throws Exception {
        MvcResult result = trackedGet("/api/mybatis/menus/page", "page01", false);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", requestId)
                        .param("pageId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isNotEmpty())
                .andExpect(jsonPath("$.logs[0].sql", not(containsString("?"))));
    }

    @Test
    void capturesJpaSelectsThroughTheSameJdbcBoundary() throws Exception {
        MvcResult result = trackedGet("/api/jpa/menus/page", "page02", false);
        String requestId = result.getResponse().getHeader("X-Request-Id");

        String response = mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", requestId)
                        .param("pageId", "page02"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode logs = objectMapper.readTree(response).get("logs");
        assertThat(logs).isNotEmpty();
        assertThat(logs.toString().toLowerCase()).contains("admin_menu");
        assertThat(logs.toString()).doesNotContain("?");
    }

    @Test
    void pausedRequestIsNeverWrittenAndResumeCapturesOnlyTheNewRequest() throws Exception {
        MvcResult paused = trackedGet("/api/mybatis/menus/page", "page01", true);
        assertLookupSize(paused, "page01", 0);

        MvcResult resumed = trackedGet("/api/mybatis/menus/page", "page01", false);
        assertLookupHasRows(resumed, "page01");
        assertLookupSize(paused, "page01", 0);
    }

    @Test
    void requestsWithoutPageIdAndNonGetRequestsAreNotTraced() throws Exception {
        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("nameKeyword", menuName)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));

        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .header("X-Page-Id", "page01")
                        .header("X-Sql-Capture-Paused", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [],
                                  "deletedIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));
    }

    @Test
    void sqlLogEndpointDoesNotCreateAnotherTrace() throws Exception {
        MvcResult business = trackedGet("/api/mybatis/menus/page", "page01", false);
        String requestId = business.getResponse().getHeader("X-Request-Id");

        mockMvc.perform(get("/api/sql-logs")
                        .header("X-Page-Id", "page01")
                        .header("X-Sql-Capture-Paused", "false")
                        .param("requestId", requestId)
                        .param("pageId", "page01"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));
    }

    private MvcResult trackedGet(String path, String pageId, boolean paused) throws Exception {
        return mockMvc.perform(get(path)
                        .header("X-Page-Id", pageId)
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

    private void assertLookupSize(MvcResult businessResult, String pageId, int size) throws Exception {
        String requestId = businessResult.getResponse().getHeader("X-Request-Id");
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", requestId)
                        .param("pageId", pageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs", hasSize(size)));
    }

    private void assertLookupHasRows(MvcResult businessResult, String pageId) throws Exception {
        String requestId = businessResult.getResponse().getHeader("X-Request-Id");
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", requestId)
                        .param("pageId", pageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isNotEmpty());
    }

    private void deleteLogDirectory() throws Exception {
        if (Files.notExists(LOG_DIRECTORY)) {
            return;
        }
        try (var paths = Files.walk(LOG_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
