package com.example.admin.sqltrace.context;

import com.example.admin.common.security.LoginUsers;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.util.StringUtils;

public final class SqlTraceUserId {

    public static final String USER_ID_ATTRIBUTE = "USER_ID";

    private SqlTraceUserId() {
    }

    public static Optional<String> resolve(HttpServletRequest request) {
        return LoginUsers.currentUsername()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .or(() -> requestAttribute(request));
    }

    private static Optional<String> requestAttribute(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID_ATTRIBUTE);
        return value == null
                ? Optional.empty()
                : Optional.of(value.toString())
                        .filter(StringUtils::hasText)
                        .map(String::trim);
    }
}
