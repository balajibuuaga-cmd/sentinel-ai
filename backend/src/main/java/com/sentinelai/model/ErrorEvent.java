package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per unhandled server error (HTTP 500) surfaced through
 * GlobalApiExceptionHandler. Self-contained, in-app error tracking — the
 * exception type, a truncated safe message, the request path/method, and the
 * correlation request id — so operators can see what's breaking without an
 * external service. Never stores stack traces or request bodies.
 */
@Entity
@Table(name = "error_events")
public class ErrorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String errorType;

    @Column(length = 1000)
    private String message;

    @Column(length = 500)
    private String path;

    @Column(length = 16)
    private String httpMethod;

    private String requestId;

    @Column(nullable = false)
    private Instant occurredAt;

    protected ErrorEvent() {
    }

    public ErrorEvent(
            String tenantId,
            String organizationName,
            String errorType,
            String message,
            String path,
            String httpMethod,
            String requestId,
            Instant occurredAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.errorType = errorType;
        this.message = message;
        this.path = path;
        this.httpMethod = httpMethod;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
