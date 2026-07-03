package com.aura.ajo.filter;

import com.aura.ajo.entity.Integrator;
import com.aura.ajo.repository.IntegratorRepository;
import com.aura.ajo.util.HashUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

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
                .findByApiKeyHash(HashUtils.sha256Hex(rawKey))
                .orElse(null);

        if (integrator == null) {
            sendUnauthorized(response, "Invalid API key");
            return;
        }

        request.setAttribute("integratorId", integrator.getId());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        integrator, null, List.of(new SimpleGrantedAuthority("ROLE_INTEGRATOR")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isExempt(String method, String path) {
        // Registration is open; Nomba webhooks use HMAC signature auth, not API key
        // Swagger UI paths are public so judges can browse docs without a key
        return ("POST".equalsIgnoreCase(method) && "/api/v1/integrators/register".equals(path))
                || path.startsWith("/webhooks/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.equals("/api-docs")
                || path.startsWith("/api-docs/");
    }

    private static void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
