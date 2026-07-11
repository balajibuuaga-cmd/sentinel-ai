package com.sentinelai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // Abusable auth endpoints get a separate, tighter per-IP budget: brute-force
    // login, credential stuffing, and signup/reset spam all target these.
    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/mfa/verify",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/confirm"
    );

    private final RateLimitService rateLimitService;

    public RateLimitingFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        boolean sensitive = "POST".equalsIgnoreCase(request.getMethod())
                && SENSITIVE_PATHS.contains(request.getRequestURI());
        RateLimitDecision decision = sensitive ? rateLimitService.checkSensitive(key) : rateLimitService.check(key);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Backend", decision.backend());
        if (decision.exceeded()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
