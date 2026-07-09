package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false, length = 1400)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String tenantId, String organizationName, String actor, String action, String target, String details, Instant createdAt) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
        this.createdAt = createdAt;
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

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
