package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "memory_links")
public class MemoryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private Long deploymentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "engineering_event_id", nullable = false)
    private EngineeringEvent engineeringEvent;

    @Column(nullable = false)
    private String patternType;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false)
    private Instant createdAt;

    protected MemoryLink() {
    }

    public MemoryLink(
            String tenantId,
            String organizationName,
            Long deploymentId,
            EngineeringEvent engineeringEvent,
            String patternType,
            String reason,
            int confidence,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.deploymentId = deploymentId;
        this.engineeringEvent = engineeringEvent;
        this.patternType = patternType;
        this.reason = reason;
        this.confidence = confidence;
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

    public Long getDeploymentId() {
        return deploymentId;
    }

    public EngineeringEvent getEngineeringEvent() {
        return engineeringEvent;
    }

    public String getPatternType() {
        return patternType;
    }

    public String getReason() {
        return reason;
    }

    public int getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
