package com.example.admin.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.List;
import com.example.admin.common.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    void usesLoginUserPrincipalUsername() {
        var loginUser = LoginUser.sessionUser("login-user", "Login User",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(loginUser, "n/a", loginUser.getAuthorities()));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("login-user");
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
