package com.aura.ajo.filter;

import com.aura.ajo.entity.Integrator;
import com.aura.ajo.repository.IntegratorRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final IntegratorRepository integratorRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isExempt(request.getMethod(), request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            sendUnauthorized(response, "Missing API key");
            return;
        }

        Integrator integrator = integratorRepository
                .findByApiKeyHash(sha256Hex(rawKey))
                .orElse(null);

        if (integrator == null) {
            sendUnauthorized(response, "Invalid API key");
            return;
        }

        request.setAttribute("integratorId", integrator.getId());
        filterChain.doFilter(request, response);
    }

    private static boolean isExempt(String method, String path) {
        // Registration is open; Nomba webhooks are authenticated by HMAC signature, not API key
        return ("POST".equalsIgnoreCase(method) && "/api/v1/integrators".equals(path))
                || path.startsWith("/webhooks/");
    }

    private static void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
