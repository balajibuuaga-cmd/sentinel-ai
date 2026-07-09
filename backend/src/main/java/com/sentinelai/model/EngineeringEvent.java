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
@Table(name = "engineering_events")
public class EngineeringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String ownerTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngineeringEventType eventType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1400)
    private String details;

    private String deploymentKey;

    private String commitSha;

    @Column(nullable = false)
    private Instant occurredAt;

    protected EngineeringEvent() {
    }

    public EngineeringEvent(
            String tenantId,
            String organizationName,
            String serviceName,
            String ownerTeam,
            EngineeringEventType eventType,
            String title,
            String details,
            String deploymentKey,
            String commitSha,
            Instant occurredAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.eventType = eventType;
        this.title = title;
        this.details = details;
        this.deploymentKey = deploymentKey;
        this.commitSha = commitSha;
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

    public String getServiceName() {
        return serviceName;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public EngineeringEventType getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }

    public String getDeploymentKey() {
        return deploymentKey;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
