package com.nem12.security;

import com.nem12.config.Nem12Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates requests against a pre-configured API key.
 * <p>
 * Expects the key in the X-API-Key header. Requests to public endpoints
 * (health checks) skip this filter — that's handled by the security config.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final Nem12Properties properties;

    public ApiKeyAuthFilter(Nem12Properties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Let public endpoints through without checking the key
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank()) {
            log.warn("Missing API key from {}: {}", request.getRemoteAddr(), requestPath);
            sendUnauthorized(response, "Missing X-API-Key header");
            return;
        }

        if (!properties.getApiKey().equals(providedKey)) {
            log.warn("Invalid API key from {}: {}", request.getRemoteAddr(), requestPath);
            sendUnauthorized(response, "Invalid API key");
            return;
        }

        // Key is valid — set up the security context so Spring Security is happy
        var authentication = new UsernamePasswordAuthenticationToken(
                "api-key-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                String.format("{\"error\": \"UNAUTHORIZED\", \"message\": \"%s\"}", message)
        );
    }
}
