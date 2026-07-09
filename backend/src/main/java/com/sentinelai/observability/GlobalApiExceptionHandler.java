package com.sentinelai.observability;

import com.sentinelai.service.integrations.ProviderSyncException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", details);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> badRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", safeMessage(ex, "The request could not be processed."), Map.of());
    }

    @ExceptionHandler(ProviderSyncException.class)
    public ResponseEntity<ApiErrorResponse> providerSync(ProviderSyncException ex) {
        return error(
                HttpStatus.BAD_GATEWAY,
                "PROVIDER_SYNC_" + ex.category().name(),
                "Provider sync failed. " + userSafeProviderMessage(ex.category().name()),
                Map.of("category", ex.category().name())
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This HTTP method is not supported for the endpoint.", Map.of());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(NoHandlerFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested endpoint was not found.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Sentinel could not complete the request.", Map.of(
                "path", request.getRequestURI()
        ));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                RequestContext.requestId(),
                code,
                message,
                details,
                Instant.now()
        ));
    }

    private String safeMessage(Exception ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        if (message.length() > 180) {
            return fallback;
        }
        return message;
    }

    private String userSafeProviderMessage(String category) {
        return switch (category) {
            case "AUTH_EXPIRED" -> "Reconnect the provider because the token is expired or unauthorized.";
            case "MISSING_SCOPE" -> "Reconnect the provider with the required scopes.";
            case "RATE_LIMITED" -> "The provider rate limit was reached. Retry after the provider resets the limit.";
            case "PROVIDER_DOWN" -> "The provider appears unavailable. Retry when the provider recovers.";
            case "BAD_CONFIG" -> "Provider configuration is incomplete.";
            default -> "Check the provider configuration and retry.";
        };
    }
}
