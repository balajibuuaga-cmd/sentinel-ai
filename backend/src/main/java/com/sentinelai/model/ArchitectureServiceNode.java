package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "architecture_services")
public class ArchitectureServiceNode {

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

    @Column(nullable = false)
    private String runtime;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private String repository;

    @Column(nullable = false, length = 1000)
    private String description;

    protected ArchitectureServiceNode() {
    }

    public ArchitectureServiceNode(
            String tenantId,
            String organizationName,
            String serviceName,
            String ownerTeam,
            String runtime,
            String tier,
            String repository,
            String description
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.runtime = runtime;
        this.tier = tier;
        this.repository = repository;
        this.description = description;
    }

    public void update(String ownerTeam, String runtime, String tier, String repository, String description) {
        this.ownerTeam = ownerTeam;
        this.runtime = runtime;
        this.tier = tier;
        this.repository = repository;
        this.description = description;
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

    public String getRuntime() {
        return runtime;
    }

    public String getTier() {
        return tier;
    }

    public String getRepository() {
        return repository;
    }

    public String getDescription() {
        return description;
    }
}
