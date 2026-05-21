package com.example.admin.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adminLearningOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Admin Learning API")
                .description("JPA and MyBatis examples for admin user, role, and menu management.")
                .version("v1"));
    }
}

