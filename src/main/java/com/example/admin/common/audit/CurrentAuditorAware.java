package com.example.admin.common.audit;

import com.example.admin.common.security.LoginUsers;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class CurrentAuditorAware implements AuditorAware<String> {

    public static final String USER_HEADER = "X-User-Id";

    @Override
    public Optional<String> getCurrentAuditor() {
        return LoginUsers.currentUsername()
                .or(this::authenticatedUsername)
                .or(this::requestHeaderUsername)
                .or(() -> Optional.of("system"));
    }

    private Optional<String> authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }

    private Optional<String> requestHeaderUsername() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        String userId = servletAttributes.getRequest().getHeader(USER_HEADER);
        return StringUtils.hasText(userId) ? Optional.of(userId) : Optional.empty();
    }
}
