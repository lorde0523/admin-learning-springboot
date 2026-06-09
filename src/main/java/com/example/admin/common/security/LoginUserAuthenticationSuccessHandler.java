package com.example.admin.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

public class LoginUserAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {
        Authentication normalizedAuthentication = normalize(authentication);
        SecurityContextHolder.getContext().setAuthentication(normalizedAuthentication);
        super.onAuthenticationSuccess(request, response, normalizedAuthentication);
    }

    private Authentication normalize(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return authentication;
        }

        LoginUser loginUser = toLoginUser(authentication);
        if (loginUser == null) {
            return authentication;
        }

        if (authentication instanceof OAuth2AuthenticationToken oauth2Authentication) {
            var token = new OAuth2AuthenticationToken(loginUser, loginUser.getAuthorities(),
                    oauth2Authentication.getAuthorizedClientRegistrationId());
            token.setDetails(authentication.getDetails());
            return token;
        }

        var token = UsernamePasswordAuthenticationToken.authenticated(
                loginUser, authentication.getCredentials(), loginUser.getAuthorities());
        token.setDetails(authentication.getDetails());
        return token;
    }

    private LoginUser toLoginUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            String provider = authentication instanceof OAuth2AuthenticationToken oauth2Authentication
                    ? oauth2Authentication.getAuthorizedClientRegistrationId()
                    : "oauth2";
            return LoginUser.from(oAuth2User, provider);
        }
        if (principal instanceof UserDetails userDetails) {
            return LoginUser.sessionUser(userDetails.getUsername(), userDetails.getUsername(),
                    userDetails.getAuthorities());
        }
        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return LoginUser.sessionUser(username, username, authentication.getAuthorities());
        }
        return null;
    }
}
