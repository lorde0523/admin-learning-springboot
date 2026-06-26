package com.example.admin.sqltrace.context;

import com.example.admin.common.security.LoginUsers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class SqlCaptureRequestFilter extends OncePerRequestFilter {

    public static final String UI_ID_HEADER = "X-Ui-Id";
    public static final String LEGACY_PAGE_ID_HEADER = "X-Page-Id";
    public static final String PAUSED_HEADER = "X-Sql-Capture-Paused";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                || "/api/sql-logs".equals(applicationPath(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        String uiId = request.getHeader(UI_ID_HEADER);
        if (!StringUtils.hasText(uiId)) {
            uiId = request.getHeader(LEGACY_PAGE_ID_HEADER);
        }
        if (!StringUtils.hasText(uiId)) {
            filterChain.doFilter(request, response);
            return;
        }
        String authenticatedUsername = LoginUsers.currentUsername().orElse(null);
        if (!StringUtils.hasText(authenticatedUsername)) {
            filterChain.doFilter(request, response);
            return;
        }
        String userId = SqlTraceUserId.resolve(request, authenticatedUsername);

        Boolean paused = parsePaused(request.getHeader(PAUSED_HEADER));
        if (paused == null) {
            response.sendError(
                    HttpStatus.BAD_REQUEST.value(),
                    "X-Sql-Capture-Paused must be true or false.");
            return;
        }

        String requestId = UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID_HEADER, requestId);
        SqlCaptureContextHolder.set(new SqlCaptureContext(
                userId,
                requestId,
                uiId.trim(),
                paused));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SqlCaptureContextHolder.clear();
        }
    }

    private Boolean parsePaused(String header) {
        if (!StringUtils.hasText(header)) {
            return false;
        }
        if ("true".equalsIgnoreCase(header)) {
            return true;
        }
        if ("false".equalsIgnoreCase(header)) {
            return false;
        }
        return null;
    }

    private String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return StringUtils.hasText(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }
}
