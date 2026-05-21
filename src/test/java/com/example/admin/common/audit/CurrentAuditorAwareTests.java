package com.example.admin.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentAuditorAwareTests {

    private final CurrentAuditorAware auditorAware = new CurrentAuditorAware();

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesAuthenticatedUsernameBeforeRequestHeader() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("security-user", "n/a", null));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("security-user");
    }

    @Test
    void usesRequestHeaderWhenNoAuthenticatedUserExists() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("header-user");
    }

    @Test
    void fallsBackToSystemWhenRequestHasNoUser() {
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}

