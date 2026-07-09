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
@Table(name = "architecture_dependencies")
public class ArchitectureDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String sourceService;

    @Column(nullable = false)
    private String targetService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchitectureDependencyType dependencyType;

    @Column(nullable = false)
    private String criticality;

    @Column(nullable = false, length = 1000)
    private String notes;

    protected ArchitectureDependency() {
    }

    public ArchitectureDependency(
            String tenantId,
            String organizationName,
            String sourceService,
            String targetService,
            ArchitectureDependencyType dependencyType,
            String criticality,
            String notes
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.sourceService = sourceService;
        this.targetService = targetService;
        this.dependencyType = dependencyType;
        this.criticality = criticality;
        this.notes = notes;
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

    public String getSourceService() {
        return sourceService;
    }

    public String getTargetService() {
        return targetService;
    }

    public ArchitectureDependencyType getDependencyType() {
        return dependencyType;
    }

    public String getCriticality() {
        return criticality;
    }

    public String getNotes() {
        return notes;
    }
}
