package com.example.admin.sqltrace.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.common.security.LoginUser;
import com.example.admin.sqltrace.storage.SqlTraceEntry;
import com.example.admin.sqltrace.storage.SqlTraceStore;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SqlCaptureRequestFilterTests {

    private static final OffsetDateTime API_STARTED_AT =
            OffsetDateTime.parse("2026-07-01T10:30:15.100+09:00");

    private final StubStore store = new StubStore();
    private final AtomicLong nanoTime = new AtomicLong();
    private final SqlCaptureRequestFilter filter = new SqlCaptureRequestFilter(
            store,
            Clock.fixed(
                    Instant.parse("2026-07-01T01:30:15.100Z"),
                    ZoneId.of("Asia/Seoul")),
            () -> nanoTime.getAndAdd(500_000_000L));

    @BeforeEach
    void authenticate() {
        LoginUser loginUser = LoginUser.sessionUser("user1", "User 1", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        loginUser,
                        "",
                        loginUser.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        SqlCaptureContextHolder.clear();
    }

    @Test
    void createsContextAndResponseHeaderForTrackedGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        request.addHeader("X-Sql-Capture-Paused", "false");
        request.setAttribute("USER_ID", "attribute-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current().orElseThrow()));

        assertThat(observed.get().traceType()).isEqualTo(SqlTraceType.QUERY);
        assertThat(observed.get().uiId()).isEqualTo("page01");
        assertThat(observed.get().userId()).isEqualTo("attribute-user");
        assertThat(observed.get().apiStartedAt()).isEqualTo(API_STARTED_AT);
        assertThat(observed.get().sqlCapturePaused()).isFalse();
        assertThat(response.getHeader("X-Api-Started-At"))
                .isEqualTo("2026-07-01T10:30:15.100+09:00");
        assertThat(response.getHeader("X-Request-Id")).isNull();
        assertThat(store.serverTimingUpdates).isEqualTo(1);
        assertThat(store.lastServerTimeMillis).isEqualTo(500);
        assertThat(SqlCaptureContextHolder.current()).isEmpty();
    }

    @Test
    void defaultsMissingPauseHeaderToCaptureEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", " page01 ");
        request.setAttribute("USER_ID", "attribute-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current().orElseThrow()));

        assertThat(observed.get().uiId()).isEqualTo("page01");
        assertThat(observed.get().sqlCapturePaused()).isFalse();
    }

    @Test
    void usesRequestAttributeWithoutAuthenticatedLoginUser() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        request.setAttribute("USER_ID", "attribute-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current().orElseThrow()));

        assertThat(observed.get().userId()).isEqualTo("attribute-user");
    }

    @Test
    void doesNotCreateContextWithoutPageId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(SqlCaptureContextHolder.current()).isEmpty());

        assertThat(response.getHeader("X-Api-Started-At")).isNull();
    }

    @Test
    void doesNotCreateContextWithoutUserIdAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(SqlCaptureContextHolder.current()).isEmpty());

        assertThat(response.getHeader("X-Api-Started-At")).isNull();
    }

    @Test
    void rejectsInvalidPauseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        request.addHeader("X-Sql-Capture-Paused", "invalid");
        request.setAttribute("USER_ID", "attribute-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(SqlCaptureContextHolder.current()).isEmpty();
    }

    @Test
    void clearsContextWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        request.setAttribute("USER_ID", "attribute-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new ServletException("failure");
            });
        } catch (ServletException | IOException expected) {
            assertThat(expected).hasMessage("failure");
        }

        assertThat(SqlCaptureContextHolder.current()).isEmpty();
    }

    @Test
    void skipsSqlLogEndpointAndNonGetRequests() throws Exception {
        assertThat(runAndObserve("GET", "/api/sql-logs", "page01")).isEmpty();
        assertThat(runAndObserve("POST", "/api/jpa/menus/grid-save", "page01")).isEmpty();
    }

    @Test
    void skipsCaptureForMissingOrInvalidTraceMetadata() throws Exception {
        assertThat(runAndObserveWithoutTraceType()).isEmpty();
        assertThat(runAndObserve("GET", "/api/jpa/menus/page", "page:01")).isEmpty();
        assertThat(runAndObserve("GET", "/api/jpa/menus/page", "한글화면")).isEmpty();

        MockHttpServletRequest request = trackedGet("page01");
        request.removeHeader("X-Trace-Type");
        request.addHeader("X-Trace-Type", "unknown");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(SqlCaptureContextHolder.current()).isEmpty());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Api-Started-At")).isNull();
    }

    @Test
    void skipsSqlLogEndpointUnderServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/api/sql-logs");
        request.setContextPath("/admin");
        request.setServletPath("/api/sql-logs");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", "page01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<SqlCaptureContext>> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current()));

        assertThat(observed.get()).isEmpty();
        assertThat(response.getHeader("X-Api-Started-At")).isNull();
    }

    private Optional<SqlCaptureContext> runAndObserve(String method, String uri, String pageId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", pageId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<SqlCaptureContext>> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current()));

        return observed.get();
    }

    private Optional<SqlCaptureContext> runAndObserveWithoutTraceType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Ui-Id", "page01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<SqlCaptureContext>> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current()));

        return observed.get();
    }

    private MockHttpServletRequest trackedGet(String uiId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Trace-Type", "query");
        request.addHeader("X-Ui-Id", uiId);
        return request;
    }

    private static class StubStore implements SqlTraceStore {

        private int serverTimingUpdates;
        private long lastServerTimeMillis;

        @Override
        public void append(SqlTraceEntry entry) {
        }

        @Override
        public List<SqlTraceEntry> find(
                SqlTraceType traceType,
                String userId,
                String uiId) {
            return List.of();
        }

        @Override
        public long updateServerTiming(
                SqlTraceType traceType,
                String userId,
                String uiId,
                OffsetDateTime apiStartedAt,
                long serverTimeMillis) {
            serverTimingUpdates++;
            lastServerTimeMillis = serverTimeMillis;
            return 1;
        }

        @Override
        public long updateClientTiming(
                SqlTraceType traceType,
                String userId,
                String uiId,
                OffsetDateTime apiStartedAt,
                double clientTimeMillis,
                double totalTimeMillis) {
            return 0;
        }

        @Override
        public void delete(SqlTraceType traceType, String userId, String uiId) {
        }
    }
}
