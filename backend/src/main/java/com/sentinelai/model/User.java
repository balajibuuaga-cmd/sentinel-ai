package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    private String resetTokenHash;

    private Instant resetTokenExpiresAt;

    protected User() {
    }

    public User(String tenantId, String organizationName, String email, String passwordHash, String role, Instant createdAt) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.failedLoginAttempts = 0;
        this.createdAt = createdAt;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration) {
        failedLoginAttempts += 1;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = Instant.now().plus(lockoutDuration);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = Instant.now();
    }

    public void issuePasswordResetToken(String tokenHash, Duration validity) {
        this.resetTokenHash = tokenHash;
        this.resetTokenExpiresAt = Instant.now().plus(validity);
    }

    public boolean matchesResetToken(String tokenHash) {
        return resetTokenHash != null
                && resetTokenExpiresAt != null
                && resetTokenExpiresAt.isAfter(Instant.now())
                && resetTokenHash.equals(tokenHash);
    }

    public void applyPasswordReset(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.resetTokenHash = null;
        this.resetTokenExpiresAt = null;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void changeRole(String role) {
        this.role = role;
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
