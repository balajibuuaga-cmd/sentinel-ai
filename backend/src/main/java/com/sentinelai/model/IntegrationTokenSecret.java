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
@Table(name = "integration_token_secrets")
public class IntegrationTokenSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String secretRef;

    @Column(nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationProvider provider;

    @Column(nullable = false, length = 4096)
    private String encryptedAccessToken;

    @Column(length = 4096)
    private String encryptedRefreshToken;

    @Column(nullable = false)
    private String tokenFingerprint;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected IntegrationTokenSecret() {
    }

    public IntegrationTokenSecret(
            String secretRef,
            String tenantId,
            IntegrationProvider provider,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            String tokenFingerprint,
            Instant now
    ) {
        this.secretRef = secretRef;
        this.tenantId = tenantId;
        this.provider = provider;
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.tokenFingerprint = tokenFingerprint;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rotate(String encryptedAccessToken, String encryptedRefreshToken, String tokenFingerprint) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.tokenFingerprint = tokenFingerprint;
        this.updatedAt = Instant.now();
    }

    public String getSecretRef() {
        return secretRef;
    }

    public String getTenantId() {
        return tenantId;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public String getTokenFingerprint() {
        return tokenFingerprint;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }
}
