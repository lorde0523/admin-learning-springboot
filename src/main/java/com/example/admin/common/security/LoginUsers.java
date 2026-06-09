package com.example.admin.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class LoginUsers {

    private LoginUsers() {
    }

    public static Optional<LoginUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser) {
            return Optional.of(loginUser);
        }
        return Optional.empty();
    }

    public static Optional<String> currentUsername() {
        return current().map(LoginUser::getUsername);
    }
}
