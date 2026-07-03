package com.aura.ajo.config;

import com.aura.ajo.repository.IntegratorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public paths that bypass API key authentication:
     *   - POST /api/v1/integrators/register  — bootstrap; caller has no key yet
     *   - POST /webhooks/nomba               — Nomba signs with HMAC; key auth would conflict
     *   - /api/v1/test/**                    — dev-only; gated by @Profile("dev")
     *   - GET /health                        — infrastructure / load-balancer probe
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/integrators/register",
            "/webhooks/nomba",
            "/api/v1/test/**",
            "/health"
    };

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(IntegratorRepository integratorRepository) {
        return new ApiKeyAuthFilter(integratorRepository);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
