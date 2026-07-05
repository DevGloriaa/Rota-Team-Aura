package com.aura.ajo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rotaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rota API")
                        .description("Rotating savings infrastructure — dedicated virtual accounts and " +
                                "automated group savings for any platform built on Nomba.\n\n" +
                                "## Quick Start\n" +
                                "1. **Register** → `POST /api/v1/integrators/register` with `{\"name\": \"Your App\"}` — no key needed\n" +
                                "2. **Copy your API key** from the response (shown once only)\n" +
                                "3. **Click Authorize** (top right) → paste your key → click Authorize\n" +
                                "4. All endpoints are now unlocked — start with `POST /api/v1/groups` to create a savings group\n\n" +
                                "Built on Nomba Virtual Accounts, Webhooks, and Transfers APIs.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Team Aura")
                                .email("glo.obiorah@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"))
                .components(new Components().addSecuritySchemes("ApiKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")))
                // Explicit tag order — Swagger UI renders tags in the order they're declared
                // here, regardless of controller class order: register (get an API key)
                // before using the main Groups feature, then Webhooks, then Quarantine last.
                .tags(List.of(
                        new Tag().name("Integrators").description("Register and manage API access"),
                        new Tag().name("Groups").description("Savings group lifecycle management"),
                        new Tag().name("Webhooks").description("Inbound payment notifications"),
                        new Tag().name("Quarantine").description("Misdirected payment handling")
                ));
    }
}
