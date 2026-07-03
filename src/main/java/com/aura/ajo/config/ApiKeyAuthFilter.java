package com.aura.ajo.config;

import com.aura.ajo.repository.IntegratorRepository;
import com.aura.ajo.serviceImpl.IntegratorServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the X-Api-Key header on every protected request.
 * The raw key is hashed with SHA-256 before the DB lookup — the plain key is never stored.
 */
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Api-Key";

    private final IntegratorRepository integratorRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            rejectUnauthorized(response, "Missing " + API_KEY_HEADER + " header");
            return;
        }

        String keyHash = IntegratorServiceImpl.sha256Hex(rawKey);
        boolean valid = integratorRepository.findByApiKeyHash(keyHash).isPresent();

        if (!valid) {
            log.warn("Rejected request to {} — invalid API key", request.getRequestURI());
            rejectUnauthorized(response, "Invalid API key");
            return;
        }

        chain.doFilter(request, response);
    }

    private static void rejectUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
