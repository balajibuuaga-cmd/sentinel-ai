package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Canonical registry of tenants. Every {@code tenant_id} used across the system
 * resolves to a row here, and {@code users.tenant_id} has a foreign key to it, so
 * a user can never belong to a tenant that does not exist. This is also the home
 * for future tenant-level metadata (plan, limits, settings).
 *
 * <p>{@code organization_name} remains denormalized on the domain tables as a
 * deliberate read-optimization; this table is the system of record for it.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    public Tenant(String tenantId, String organizationName, Instant createdAt) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.createdAt = createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
