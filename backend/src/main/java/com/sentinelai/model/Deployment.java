package com.sentinelai.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String deploymentKey;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String ownerTeam;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String commitSha;

    @Column(nullable = false, length = 500)
    private String pullRequestTitle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "deployment_dependencies", joinColumns = @JoinColumn(name = "deployment_id"))
    @Column(name = "dependency_name")
    @OrderColumn(name = "sort_order")
    private List<String> dependencies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "deployment_signals", joinColumns = @JoinColumn(name = "deployment_id"))
    @OrderColumn(name = "sort_order")
    private List<Signal> signals = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Embedded
    private PersistedRiskAssessment persistedRiskAssessment = new PersistedRiskAssessment();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "deployment_risk_reasons", joinColumns = @JoinColumn(name = "deployment_id"))
    @OrderColumn(name = "sort_order")
    private List<RiskReason> riskReasons = new ArrayList<>();

    protected Deployment() {
    }

    public Deployment(
            long id,
            String tenantId,
            String organizationName,
            String deploymentKey,
            String serviceName,
            String ownerTeam,
            String environment,
            String commitSha,
            String pullRequestTitle,
            List<String> dependencies,
            List<Signal> signals,
            Instant createdAt,
            DeploymentStatus status
    ) {
        this.id = null;
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.deploymentKey = deploymentKey;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.environment = environment;
        this.commitSha = commitSha;
        this.pullRequestTitle = pullRequestTitle;
        this.dependencies = new ArrayList<>(dependencies);
        this.signals = new ArrayList<>(signals);
        this.createdAt = createdAt;
        this.status = status;
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

    public String getCommitSha() {
        return commitSha;
    }

    public String getPullRequestTitle() {
        return pullRequestTitle;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<Signal> getSignals() {
        return signals;
    }

    public void addSignals(List<Signal> signals) {
        this.signals.addAll(signals);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public RiskAssessment getRiskAssessment() {
        if (persistedRiskAssessment == null || persistedRiskAssessment.getLevel() == null) {
            return null;
        }
        return new RiskAssessment(
                persistedRiskAssessment.getScore(),
                persistedRiskAssessment.getLevel(),
                persistedRiskAssessment.getRecommendation(),
                persistedRiskAssessment.getAiExplanation(),
                riskReasons,
                persistedRiskAssessment.getAssessedAt()
        );
    }

    public void setRiskAssessment(RiskAssessment riskAssessment) {
        if (riskAssessment == null) {
            this.persistedRiskAssessment = new PersistedRiskAssessment();
            this.riskReasons = new ArrayList<>();
            return;
        }
        this.persistedRiskAssessment = new PersistedRiskAssessment(
                riskAssessment.score(),
                riskAssessment.level(),
                riskAssessment.recommendation(),
                riskAssessment.aiExplanation(),
                riskAssessment.assessedAt()
        );
        this.riskReasons = new ArrayList<>(riskAssessment.reasons());
    }
}
