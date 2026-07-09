package com.sentinelai.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
@Table(name = "service_profiles")
public class ServiceProfile {

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
    private String productionCriticality;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_profile_dependencies", joinColumns = @JoinColumn(name = "service_profile_id"))
    @Column(name = "dependency_name")
    @OrderColumn(name = "sort_order")
    private List<String> dependencies = new ArrayList<>();

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected ServiceProfile() {
    }

    public ServiceProfile(
            String tenantId,
            String organizationName,
            String serviceName,
            String ownerTeam,
            String productionCriticality,
            List<String> dependencies,
            Instant lastSeenAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.productionCriticality = productionCriticality;
        this.dependencies = new ArrayList<>(dependencies);
        this.lastSeenAt = lastSeenAt;
    }

    public void update(String ownerTeam, String productionCriticality, List<String> dependencies, Instant lastSeenAt) {
        this.ownerTeam = ownerTeam;
        this.productionCriticality = productionCriticality;
        this.dependencies = new ArrayList<>(dependencies);
        this.lastSeenAt = lastSeenAt;
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

    public String getProductionCriticality() {
        return productionCriticality;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
