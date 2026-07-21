package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;

@Entity
@Table(name = "integration_connections")
public class IntegrationConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationStatus status;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String installUrl;

    @Transient
    private boolean oauthAvailable;

    @Column(nullable = false)
    private String scopes;

    @Column(nullable = false)
    private String tokenSecretRef;

    private String externalAccount;

    private Instant connectedAt;

    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationSyncStatus lastSyncStatus;

    @Column(nullable = false)
    private int healthScore;

    @Column(nullable = false, length = 1000)
    private String statusDetail;

    protected IntegrationConnection() {
    }

    public IntegrationConnection(
            String tenantId,
            String organizationName,
            IntegrationProvider provider,
            String displayName,
            String installUrl,
            String scopes,
            String tokenSecretRef,
            String statusDetail
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.provider = provider;
        this.status = IntegrationStatus.AVAILABLE;
        this.displayName = displayName;
        this.installUrl = installUrl;
        this.scopes = scopes;
        this.tokenSecretRef = tokenSecretRef;
        this.statusDetail = statusDetail;
        this.lastSyncStatus = IntegrationSyncStatus.SUCCESS;
        this.healthScore = 0;
    }

    public void connect(String externalAccount) {
        connect(externalAccount, this.tokenSecretRef, "Connected to " + externalAccount + ". Initial sync completed.");
    }

    public void connect(String externalAccount, String tokenSecretRef, String detail) {
        this.status = IntegrationStatus.CONNECTED;
        this.externalAccount = externalAccount;
        this.tokenSecretRef = tokenSecretRef;
        this.connectedAt = Instant.now();
        this.lastSyncAt = Instant.now();
        this.lastSyncStatus = IntegrationSyncStatus.SUCCESS;
        this.healthScore = 100;
        this.statusDetail = detail;
    }

    public void refreshConfiguration(String installUrl, String scopes, String tokenSecretRef) {
        this.installUrl = installUrl;
        this.scopes = scopes;
        this.tokenSecretRef = tokenSecretRef;
    }

    public void sync(IntegrationSyncStatus syncStatus, int healthScore, String detail) {
        this.status = IntegrationStatus.CONNECTED;
        this.lastSyncAt = Instant.now();
        this.lastSyncStatus = syncStatus;
        this.healthScore = healthScore;
        this.statusDetail = detail;
        if (syncStatus == IntegrationSyncStatus.DEGRADED) {
            this.status = IntegrationStatus.NEEDS_ATTENTION;
        }
        if (syncStatus == IntegrationSyncStatus.FAILED) {
            this.status = IntegrationStatus.NEEDS_ATTENTION;
        }
    }

    public void disconnect() {
        this.status = IntegrationStatus.DISCONNECTED;
        this.healthScore = 0;
        this.statusDetail = "Integration disconnected. Stored token reference should be revoked in the provider.";
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

    public IntegrationProvider getProvider() {
        return provider;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInstallUrl() {
        return installUrl;
    }

    /**
     * Whether the browser should be sent through the provider's OAuth flow.
     *
     * <p>Not derived from installUrl: the authorize endpoint is a constant, so
     * that URL exists whether or not a client id and secret are configured.
     * Only the service knows if a real exchange can complete, so it sets this.
     */
    public boolean isOauthAvailable() {
        return oauthAvailable;
    }

    public void setOauthAvailable(boolean oauthAvailable) {
        this.oauthAvailable = oauthAvailable;
    }

    public String getScopes() {
        return scopes;
    }

    public String getTokenSecretRef() {
        return tokenSecretRef;
    }

    public String getExternalAccount() {
        return externalAccount;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public IntegrationSyncStatus getLastSyncStatus() {
        return lastSyncStatus;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public String getStatusDetail() {
        return statusDetail;
    }
}
