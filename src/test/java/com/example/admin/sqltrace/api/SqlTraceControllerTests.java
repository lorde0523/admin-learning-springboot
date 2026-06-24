package com.example.admin.sqltrace.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.common.exception.ApiExceptionHandler;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SqlTraceControllerTests {

    private static final String REQUEST_ID = "2de4a7d7-1453-4652-85dd-ef8ddfa57467";

    private final StubStore store = new StubStore();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SqlTraceController(store))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsRowsMatchingRequestAndPage() throws Exception {
        store.entries = List.of(entry());

        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", REQUEST_ID)
                        .param("pageId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", is(REQUEST_ID)))
                .andExpect(jsonPath("$.pageId", is("page01")))
                .andExpect(jsonPath("$.logs", hasSize(1)))
                .andExpect(jsonPath("$.logs[0].sql", is("select 1")))
                .andExpect(jsonPath("$.logs[0].elapsedMillis").value(4));
    }

    @Test
    void rejectsNonUuidRequestId() throws Exception {
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", "not-a-uuid")
                        .param("pageId", "page01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void rejectsNonCanonicalUuidRequestId() throws Exception {
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", "1-1-1-1-1")
                        .param("pageId", "page01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankPageId() throws Exception {
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", REQUEST_ID)
                        .param("pageId", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsEmptyArrayWhenNoRowsExist() throws Exception {
        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", REQUEST_ID)
                        .param("pageId", "page01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs", hasSize(0)));
    }

    @Test
    void returnsServerErrorWhenFileReadFails() throws Exception {
        store.failure = new IOException("read failed");

        mockMvc.perform(get("/api/sql-logs")
                        .param("requestId", REQUEST_ID)
                        .param("pageId", "page01"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("HTTP_500")));
    }

    private SqlTraceEntry entry() {
        return new SqlTraceEntry(
                REQUEST_ID,
                "page01",
                OffsetDateTime.parse("2026-06-25T14:20:31+09:00"),
                4,
                "select 1");
    }

    private static class StubStore implements SqlTraceStore {

        private List<SqlTraceEntry> entries = List.of();
        private IOException failure;

        @Override
        public void append(SqlTraceEntry entry) {
        }

        @Override
        public List<SqlTraceEntry> find(String requestId, String pageId) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return entries;
        }
    }
}
