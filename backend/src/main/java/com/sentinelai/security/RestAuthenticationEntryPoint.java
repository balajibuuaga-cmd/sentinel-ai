package com.sentinelai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = response.getHeader("X-Request-ID");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", sanitizeRequestId(requestId));
        body.put("code", "UNAUTHENTICATED");
        body.put("message", "Authentication is required to access this resource.");
        body.put("details", Map.of());
        body.put("timestamp", Instant.now().toString());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Serialize via Jackson rather than string-formatting: the request id is
        // client-controlled, and Jackson escapes it correctly so it can never
        // break out of the JSON string or inject markup into the response.
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * A correlation id is echoed back to the caller, so the client-supplied
     * X-Request-ID must not be trusted verbatim. Keep only the characters a real
     * correlation id uses and cap the length, so nothing surprising reaches the
     * response body or the logs it is later correlated against.
     */
    private static String sanitizeRequestId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.length() > 64 ? raw.substring(0, 64) : raw;
        return trimmed.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
