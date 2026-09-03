package com.awesomesoft.features.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "BearerAuth";
    public static final String BASIC_AUTH = "BasicAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Feature Flag Service API")
                        .description("Admin panel API and evaluation endpoint for AwesomeSoft feature flags")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Backend token for the Evaluation API (Authorization: Bearer <token>)"))
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Admin panel credentials")));
    }
}
