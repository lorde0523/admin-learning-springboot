package com.example.admin.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

public record LoginUser(
        Long id,
        String username,
        String displayName,
        String email,
        String provider,
        String providerId,
        Map<String, Object> attributes,
        List<SimpleGrantedAuthority> authorities
) implements UserDetails, OAuth2User, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public LoginUser {
        Objects.requireNonNull(username, "username must not be null");
        displayName = StringUtils.hasText(displayName) ? displayName : username;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }

    public static LoginUser sessionUser(String username, String displayName,
            Collection<? extends GrantedAuthority> authorities) {
        return new LoginUser(null, username, displayName, null, "session", username,
                Map.of(), toSimpleAuthorities(authorities));
    }

    public static LoginUser from(OAuth2User oAuth2User) {
        return from(oAuth2User, "oauth2");
    }

    public static LoginUser from(OAuth2User oAuth2User, String provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String username = firstText(attributes, "sub", "id", "login", "email", "name");
        if (!StringUtils.hasText(username)) {
            username = oAuth2User.getName();
        }
        String displayName = firstText(attributes, "name", "preferred_username", "login", "email");
        String email = firstText(attributes, "email");

        return new LoginUser(null, username, displayName, email, provider, oAuth2User.getName(),
                attributes, toSimpleAuthorities(oAuth2User.getAuthorities()));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static List<SimpleGrantedAuthority> toSimpleAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return List.of();
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private static String firstText(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }
}
