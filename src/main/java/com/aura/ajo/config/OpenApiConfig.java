package com.aura.ajo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rotaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rota API")
                        .description("Rotating savings infrastructure — dedicated virtual accounts and automated group savings for any platform built on Nomba")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Team Aura")
                                .email("glo.obiorah@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"))
                .components(new Components().addSecuritySchemes("ApiKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")));
    }
}
