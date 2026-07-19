package com.sentinelai.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns 401 when a request arrives without valid authentication.
 *
 * <p>Without this, Spring Security's default handling answers anonymous requests
 * to protected endpoints with 403, which is indistinguishable from "signed in but
 * not permitted". Clients cannot then tell an expired session (re-authenticate)
 * from a genuine permission boundary (stay put, show a message) — the browser
 * console surfaced an error screen on expiry instead of redirecting to login.
 *
 * <p>With this entry point registered the two cases separate cleanly:
 * 401 = not authenticated, 403 = authenticated but lacking the role.
 *
 * <p>The body mirrors {@code GlobalApiExceptionHandler} so every API failure
 * carries the same shape and a correlatable request id.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = response.getHeader("X-Request-ID");
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"requestId":"%s","code":"UNAUTHENTICATED",\
                "message":"Authentication is required to access this resource.",\
                "details":{},"timestamp":"%s"}"""
                .formatted(requestId == null ? "" : requestId, Instant.now()));
    }
}
