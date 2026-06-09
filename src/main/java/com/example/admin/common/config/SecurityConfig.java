package com.example.admin.common.config;

import com.example.admin.common.security.CustomOAuth2UserService;
import com.example.admin.common.security.LoginUserAuthenticationSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public LoginUserAuthenticationSuccessHandler loginUserAuthenticationSuccessHandler() {
        return new LoginUserAuthenticationSuccessHandler();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginUserAuthenticationSuccessHandler loginUserAuthenticationSuccessHandler,
            CustomOAuth2UserService customOAuth2UserService,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .formLogin(form -> form
                        .successHandler(loginUserAuthenticationSuccessHandler)
                        .permitAll())
                .httpBasic(Customizer.withDefaults())
                .logout(logout -> logout
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(loginUserAuthenticationSuccessHandler));
        }

        return http.build();
    }
}
