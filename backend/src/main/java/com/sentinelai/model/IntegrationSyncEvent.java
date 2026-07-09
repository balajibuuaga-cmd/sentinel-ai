package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "integration_sync_events")
public class IntegrationSyncEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private Long integrationConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationSyncStatus status;

    @Column(nullable = false)
    private int recordsInspected;

    @Column(nullable = false)
    private int latencyMs;

    @Column(nullable = false)
    private int healthScore;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected IntegrationSyncEvent() {
    }

    public IntegrationSyncEvent(
            String tenantId,
            String organizationName,
            Long integrationConnectionId,
            IntegrationProvider provider,
            IntegrationSyncStatus status,
            int recordsInspected,
            int latencyMs,
            int healthScore,
            String detail,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.integrationConnectionId = integrationConnectionId;
        this.provider = provider;
        this.status = status;
        this.recordsInspected = recordsInspected;
        this.latencyMs = latencyMs;
        this.healthScore = healthScore;
        this.detail = detail;
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

    public Long getIntegrationConnectionId() {
        return integrationConnectionId;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public IntegrationSyncStatus getStatus() {
        return status;
    }

    public int getRecordsInspected() {
        return recordsInspected;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
