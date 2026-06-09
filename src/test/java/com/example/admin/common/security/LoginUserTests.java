package com.example.admin.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class LoginUserTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsLoginUserFromOAuth2User() {
        var oAuth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                Map.of("sub", "oauth-sub", "name", "OAuth User", "email", "oauth@example.com"),
                "sub");

        LoginUser loginUser = LoginUser.from(oAuth2User);

        assertThat(loginUser.getUsername()).isEqualTo("oauth-sub");
        assertThat(loginUser.getDisplayName()).isEqualTo("OAuth User");
        assertThat(loginUser.getEmail()).isEqualTo("oauth@example.com");
        assertThat(loginUser.getName()).isEqualTo("oauth-sub");
        assertThat(loginUser.getAttributes()).containsEntry("sub", "oauth-sub");
        assertThat(loginUser.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void exposesCurrentLoginUserFromSecurityContextHolder() {
        var loginUser = LoginUser.sessionUser("security-user", "Security User",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(loginUser, "n/a", loginUser.getAuthorities()));

        assertThat(LoginUsers.current()).contains(loginUser);
        assertThat(LoginUsers.currentUsername()).contains("security-user");
    }

    @Test
    void returnsEmptyWhenAuthenticationPrincipalIsNotLoginUser() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("legacy-user", "n/a", List.of()));

        assertThat(LoginUsers.current()).isEmpty();
    }
}
