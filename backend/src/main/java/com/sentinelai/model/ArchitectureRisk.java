package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "architecture_risks")
public class ArchitectureRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchitectureRiskType riskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchitectureSeverity severity;

    @Column(nullable = false, length = 1200)
    private String explanation;

    @Column(nullable = false, length = 1200)
    private String recommendation;

    protected ArchitectureRisk() {
    }

    public ArchitectureRisk(
            String tenantId,
            String organizationName,
            String serviceName,
            ArchitectureRiskType riskType,
            ArchitectureSeverity severity,
            String explanation,
            String recommendation
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.serviceName = serviceName;
        this.riskType = riskType;
        this.severity = severity;
        this.explanation = explanation;
        this.recommendation = recommendation;
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

    public ArchitectureRiskType getRiskType() {
        return riskType;
    }

    public ArchitectureSeverity getSeverity() {
        return severity;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
