package com.redislab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class OpenApiConfig {

    private static final String ADMIN_KEY_SCHEME = "AdminKey";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Redis Engineering Lab API")
                        .description("Product and inventory API (Phase 1)")
                        .version("0.0.1"))
                .components(new Components()
                        .addSecuritySchemes(
                                ADMIN_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Admin-Key")))
                .addSecurityItem(new SecurityRequirement().addList(ADMIN_KEY_SCHEME));
    }
}
