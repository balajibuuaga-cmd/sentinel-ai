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
@Table(name = "background_jobs")
public class BackgroundJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackgroundJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackgroundJobStatus status;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private String targetLabel;

    @Column(nullable = false, length = 1200)
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(length = 1200)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    protected BackgroundJob() {
    }

    public BackgroundJob(
            String tenantId,
            String organizationName,
            BackgroundJobType jobType,
            String targetType,
            Long targetId,
            String targetLabel,
            String payload,
            int maxAttempts,
            Instant nextRunAt
    ) {
        Instant now = Instant.now();
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.jobType = jobType;
        this.status = BackgroundJobStatus.QUEUED;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.payload = payload;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.nextRunAt = nextRunAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void start() {
        this.status = BackgroundJobStatus.RUNNING;
        this.attempts += 1;
        this.updatedAt = Instant.now();
    }

    public void succeed() {
        this.status = BackgroundJobStatus.SUCCEEDED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
        this.lastError = null;
    }

    public void fail(String error, Instant nextRunAt) {
        this.lastError = error == null || error.isBlank() ? "Job failed." : error;
        this.updatedAt = Instant.now();
        if (this.attempts >= this.maxAttempts) {
            this.status = BackgroundJobStatus.FAILED;
            this.completedAt = this.updatedAt;
            return;
        }
        this.status = BackgroundJobStatus.QUEUED;
        this.nextRunAt = nextRunAt;
    }

    public void retryNow() {
        this.status = BackgroundJobStatus.QUEUED;
        this.nextRunAt = Instant.now();
        this.lastError = null;
        this.updatedAt = this.nextRunAt;
        this.completedAt = null;
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

    public BackgroundJobType getJobType() {
        return jobType;
    }

    public BackgroundJobStatus getStatus() {
        return status;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
