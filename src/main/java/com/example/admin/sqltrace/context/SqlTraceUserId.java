package com.example.admin.sqltrace.context;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.util.StringUtils;

public final class SqlTraceUserId {

    public static final String LAST_USER_COOKIE = "LASTUSER";

    private SqlTraceUserId() {
    }

    public static String resolve(HttpServletRequest request, String authenticatedUsername) {
        return lastUserCookie(request)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(authenticatedUsername);
    }

    private static Optional<String> lastUserCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> LAST_USER_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
