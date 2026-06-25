package com.example.admin.sqltrace.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.common.security.LoginUser;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
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

    private final SqlCaptureRequestFilter filter = new SqlCaptureRequestFilter();

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
        request.addHeader("X-Page-Id", "page01");
        request.addHeader("X-Sql-Capture-Paused", "false");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current().orElseThrow()));

        assertThat(observed.get().pageId()).isEqualTo("page01");
        assertThat(observed.get().username()).isEqualTo("user1");
        assertThat(observed.get().sqlCapturePaused()).isFalse();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(observed.get().requestId());
        assertThat(SqlCaptureContextHolder.current()).isEmpty();
    }

    @Test
    void defaultsMissingPauseHeaderToCaptureEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Page-Id", " page01 ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current().orElseThrow()));

        assertThat(observed.get().pageId()).isEqualTo("page01");
        assertThat(observed.get().sqlCapturePaused()).isFalse();
    }

    @Test
    void doesNotCreateContextWithoutPageId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(SqlCaptureContextHolder.current()).isEmpty());

        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    @Test
    void doesNotCreateContextWithoutAuthenticatedLoginUser() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Page-Id", "page01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(SqlCaptureContextHolder.current()).isEmpty());

        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    @Test
    void rejectsInvalidPauseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Page-Id", "page01");
        request.addHeader("X-Sql-Capture-Paused", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(SqlCaptureContextHolder.current()).isEmpty();
    }

    @Test
    void clearsContextWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
        request.addHeader("X-Page-Id", "page01");
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
    void skipsSqlLogEndpointUnderServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/api/sql-logs");
        request.setContextPath("/admin");
        request.setServletPath("/api/sql-logs");
        request.addHeader("X-Page-Id", "page01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<SqlCaptureContext>> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current()));

        assertThat(observed.get()).isEmpty();
        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    private Optional<SqlCaptureContext> runAndObserve(String method, String uri, String pageId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Page-Id", pageId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Optional<SqlCaptureContext>> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                observed.set(SqlCaptureContextHolder.current()));

        return observed.get();
    }
}
