package com.example.admin.sqltrace.context;

import com.example.admin.sqltrace.storage.SqlTraceStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class SqlCaptureRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlCaptureRequestFilter.class);

    public static final String UI_ID_HEADER = "X-Ui-Id";
    public static final String TRACE_TYPE_HEADER = "X-Trace-Type";
    public static final String PAUSED_HEADER = "X-Sql-Capture-Paused";
    public static final String API_STARTED_AT_HEADER = "X-Api-Started-At";

    private final SqlTraceStore store;
    private final Clock clock;
    private final LongSupplier nanoTime;

    public SqlCaptureRequestFilter(SqlTraceStore store, Clock clock) {
        this(store, clock, System::nanoTime);
    }

    SqlCaptureRequestFilter(SqlTraceStore store, Clock clock, LongSupplier nanoTime) {
        this.store = store;
        this.clock = clock;
        this.nanoTime = nanoTime;
    }

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

        SqlTraceType traceType = SqlTraceType.from(request.getHeader(TRACE_TYPE_HEADER))
                .orElse(null);
        if (traceType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String uiId = request.getHeader(UI_ID_HEADER);
        uiId = SqlTraceUiId.normalize(uiId).orElse(null);
        if (uiId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String userId = SqlTraceUserId.resolve(request).orElse(null);
        if (!StringUtils.hasText(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        Boolean paused = parsePaused(request.getHeader(PAUSED_HEADER));
        if (paused == null) {
            response.sendError(
                    HttpStatus.BAD_REQUEST.value(),
                    "X-Sql-Capture-Paused must be true or false.");
            return;
        }

        OffsetDateTime apiStartedAt = OffsetDateTime.now(clock);
        long startedNanos = nanoTime.getAsLong();
        response.setHeader(API_STARTED_AT_HEADER, apiStartedAt.toString());
        SqlCaptureContextHolder.set(new SqlCaptureContext(
                traceType,
                userId,
                apiStartedAt,
                uiId,
                paused));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SqlCaptureContextHolder.clear();
            long serverTimeMillis = TimeUnit.NANOSECONDS.toMillis(
                    nanoTime.getAsLong() - startedNanos);
            try {
                store.updateServerTiming(
                        traceType,
                        userId,
                        uiId,
                        apiStartedAt,
                        serverTimeMillis);
            } catch (IOException exception) {
                log.warn(
                        "Failed to update SQL trace server timing. uiId={} apiStartedAt={}",
                        uiId,
                        apiStartedAt,
                        exception);
            }
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
