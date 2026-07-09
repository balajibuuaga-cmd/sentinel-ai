package com.sentinelai.security;

import com.sentinelai.RuntimeModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiModeFilter extends OncePerRequestFilter {

    private final RuntimeModeService runtimeModeService;

    public ApiModeFilter(RuntimeModeService runtimeModeService) {
        this.runtimeModeService = runtimeModeService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (runtimeModeService.apiEnabled() || request.getRequestURI().startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"API_DISABLED\",\"message\":\"Sentinel API is disabled for this worker runtime.\"}");
    }
}
