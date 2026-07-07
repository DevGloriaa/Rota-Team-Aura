package com.aura.ajo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

    /**
     * Spring's handler-mapping registry does not preserve controller method declaration
     * order (confirmed against the live /api-docs output — paths came back interleaved
     * across controllers, not in source order), so springdoc.swagger-ui.operations-sorter=none
     * alone isn't enough: it stops Swagger UI from re-sorting, but the order it receives
     * from the server was already unordered. This customizer rebuilds the paths map in the
     * exact sequence the demo flow expects; anything not explicitly listed keeps its
     * existing relative order, appended at the end.
     *
     * Caveat: where two HTTP methods share one path ("/api/v1/groups" and
     * ".../groups/{groupId}/members"), swagger-core's PathItem model has a fixed
     * get/put/post/delete/... field order that always renders GET above POST for that path
     * — no path-level customizer can change the method order *within* a single path entry.
     */
    @Bean
    public OpenApiCustomizer groupsPathOrderCustomizer() {
        List<String> desiredOrder = List.of(
                "/api/v1/groups",
                "/api/v1/groups/{groupId}/members",
                "/api/v1/groups/{groupId}/provision",
                "/api/v1/groups/{groupId}/activate",
                "/api/v1/groups/{groupId}",
                "/api/v1/groups/{groupId}/upcoming-dues",
                "/api/v1/groups/{groupId}/members/{memberId}/statement",
                "/api/v1/groups/{groupId}/report",
                "/api/v1/groups/{groupId}/balance",
                "/api/v1/groups/{groupId}/payouts",
                "/api/v1/groups/{groupId}/cycles/{cycleNumber}/trigger-payout",
                "/api/v1/groups/{groupId}/close",
                "/api/v1/groups/{groupId}/members/{memberId}",
                "/api/v1/groups/{groupId}/members/{memberId}/kyc"
        );

        return openApi -> {
            Paths original = openApi.getPaths();
            if (original == null) {
                return;
            }
            Paths ordered = new Paths();
            for (String path : desiredOrder) {
                if (original.containsKey(path)) {
                    ordered.addPathItem(path, original.remove(path));
                }
            }
            original.forEach(ordered::addPathItem);
            openApi.setPaths(ordered);
        };
    }
}
