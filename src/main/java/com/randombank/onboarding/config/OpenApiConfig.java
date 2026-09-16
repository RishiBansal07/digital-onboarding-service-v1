package com.randombank.onboarding.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the bearer-token security scheme so Swagger UI renders an "Authorize"
 * button.
 *
 * <p>Previously the token was exposed as a plain {@code Authorization} header
 * parameter. Swagger UI does not reliably transmit a manually declared header with
 * that reserved name, so the request reached the controller with a null header.
 * Declaring it as a security scheme makes Swagger UI attach the header itself and
 * prepend the {@code Bearer } prefix, which also removes a common manual-entry
 * mistake.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI digitalOnboardingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Onboarding Service")
                        .description("""
                                Register a customer, log in with the generated password, \
                                and view the opened account.

                                To call /overview: register, then login, then click \
                                Authorize and paste the token value only (no "Bearer " prefix).\
                                """)
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Paste the token returned by /login.")));
    }
}

