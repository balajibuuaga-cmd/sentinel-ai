package com.sentinelai.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false, unique = true)
    private String incidentKey;

    @Column(nullable = false)
    private Long deploymentId;

    @Column(nullable = false)
    private String deploymentKey;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String ownerTeam;

    @Column(nullable = false)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 700)
    private String summary;

    @Column(nullable = false, length = 1000)
    private String affectedSystems;

    @Column(nullable = false, length = 1000)
    private String commanderBrief;

    @Column(nullable = false, length = 1000)
    private String recommendedAction;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant updatedAt;

    private Instant resolvedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_timeline_events", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "sort_order")
    private List<IncidentTimelineEvent> timeline = new ArrayList<>();

    protected Incident() {
    }

    public Incident(
            String tenantId,
            String organizationName,
            String incidentKey,
            Long deploymentId,
            String deploymentKey,
            String serviceName,
            String ownerTeam,
            String environment,
            IncidentSeverity severity,
            int riskScore,
            String summary,
            String affectedSystems,
            String commanderBrief,
            String recommendedAction,
            Instant openedAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.incidentKey = incidentKey;
        this.deploymentId = deploymentId;
        this.deploymentKey = deploymentKey;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.environment = environment;
        this.severity = severity;
        this.status = IncidentStatus.ACTIVE;
        this.riskScore = riskScore;
        this.summary = summary;
        this.affectedSystems = affectedSystems;
        this.commanderBrief = commanderBrief;
        this.recommendedAction = recommendedAction;
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
    }

    public void refreshFromRisk(
            IncidentSeverity severity,
            int riskScore,
            String summary,
            String affectedSystems,
            String commanderBrief,
            String recommendedAction
    ) {
        if (status == IncidentStatus.RESOLVED) {
            return;
        }
        this.severity = severity;
        this.riskScore = riskScore;
        this.summary = summary;
        this.affectedSystems = affectedSystems;
        this.commanderBrief = commanderBrief;
        this.recommendedAction = recommendedAction;
        this.updatedAt = Instant.now();
    }

    public void transition(IncidentStatus nextStatus, String actor, String note) {
        this.status = nextStatus;
        this.updatedAt = Instant.now();
        if (nextStatus == IncidentStatus.RESOLVED) {
            this.resolvedAt = this.updatedAt;
        }
        timeline.add(new IncidentTimelineEvent(
                this.updatedAt,
                actor == null || actor.isBlank() ? "sentinel-ai" : actor,
                "Status changed to " + nextStatus.name(),
                note == null || note.isBlank() ? "No operator note provided." : note
        ));
    }

    public void addTimelineEvent(String actor, String label, String detail) {
        timeline.add(new IncidentTimelineEvent(Instant.now(), actor, label, detail));
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

    public String getIncidentKey() {
        return incidentKey;
    }

    public Long getDeploymentId() {
        return deploymentId;
    }

    public String getDeploymentKey() {
        return deploymentKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public String getEnvironment() {
        return environment;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getSummary() {
        return summary;
    }

    public String getAffectedSystems() {
        return affectedSystems;
    }

    public String getCommanderBrief() {
        return commanderBrief;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public List<IncidentTimelineEvent> getTimeline() {
        return timeline;
    }
}
