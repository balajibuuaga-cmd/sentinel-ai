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
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String externalDeliveryId;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookDeliveryStatus status;

    @Column(nullable = false, length = 6000)
    private String payload;

    @Column(nullable = false)
    private String requestId;

    @Column(length = 1200)
    private String failureReason;

    @Column
    private String targetReference;

    @Column(nullable = false)
    private int replayAttempts;

    private Instant nextReplayAt;

    @Column(nullable = false)
    private int maxReplayAttempts;

    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant processedAt;

    private Instant lastReplayedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(
            String tenantId,
            String organizationName,
            String provider,
            String externalDeliveryId,
            String eventType,
            String payload,
            String requestId
    ) {
        Instant now = Instant.now();
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.provider = provider;
        this.externalDeliveryId = externalDeliveryId;
        this.eventType = eventType;
        this.status = WebhookDeliveryStatus.RECEIVED;
        this.payload = payload;
        this.requestId = requestId;
        this.replayAttempts = 0;
        this.maxReplayAttempts = 3;
        this.expiresAt = now.plusSeconds(30L * 24 * 60 * 60);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void succeed(String targetReference) {
        this.status = WebhookDeliveryStatus.SUCCEEDED;
        this.targetReference = targetReference;
        this.failureReason = null;
        this.processedAt = Instant.now();
        this.updatedAt = this.processedAt;
    }

    public void fail(String failureReason) {
        this.status = WebhookDeliveryStatus.FAILED;
        this.failureReason = failureReason(failureReason, "Webhook processing failed.");
        this.nextReplayAt = Instant.now();
        this.processedAt = Instant.now();
        this.updatedAt = this.processedAt;
    }

    public void applyReplayPolicy(int maxReplayAttempts, Instant expiresAt) {
        this.maxReplayAttempts = Math.max(1, maxReplayAttempts);
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void queueReplay(Instant nextReplayAt) {
        if (!replayEligibleAt(Instant.now())) {
            throw new IllegalStateException(replayBlockedReason(Instant.now()));
        }
        this.status = WebhookDeliveryStatus.REPLAY_QUEUED;
        this.nextReplayAt = nextReplayAt;
        this.updatedAt = Instant.now();
    }

    public void replayed(String targetReference) {
        this.status = WebhookDeliveryStatus.REPLAYED;
        this.targetReference = targetReference;
        this.failureReason = null;
        this.replayAttempts += 1;
        this.nextReplayAt = null;
        this.lastReplayedAt = Instant.now();
        this.updatedAt = this.lastReplayedAt;
    }

    public void replayFailed(String failureReason) {
        this.status = WebhookDeliveryStatus.FAILED;
        this.failureReason = failureReason(failureReason, "Webhook replay failed.");
        this.replayAttempts += 1;
        this.nextReplayAt = nextReplayAt();
        this.lastReplayedAt = Instant.now();
        this.updatedAt = this.lastReplayedAt;
    }

    public void expire() {
        this.status = WebhookDeliveryStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public boolean replayEligibleAt(Instant now) {
        if (status == WebhookDeliveryStatus.REPLAY_QUEUED) {
            return false;
        }
        if (status != WebhookDeliveryStatus.FAILED) {
            return false;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return false;
        }
        if (replayAttempts >= maxReplayAttempts) {
            return false;
        }
        return nextReplayAt == null || !nextReplayAt.isAfter(now);
    }

    public String replayEligibility() {
        Instant now = Instant.now();
        if (status == WebhookDeliveryStatus.REPLAY_QUEUED) {
            return "queued";
        }
        if (status != WebhookDeliveryStatus.FAILED) {
            return "not replayable";
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return "expired";
        }
        if (replayAttempts >= maxReplayAttempts) {
            return "max attempts reached";
        }
        if (nextReplayAt != null && nextReplayAt.isAfter(now)) {
            return "cooling down";
        }
        return "ready";
    }

    public String replayBlockedReason(Instant now) {
        if (status == WebhookDeliveryStatus.REPLAY_QUEUED) {
            return "Webhook delivery replay is already queued.";
        }
        if (status != WebhookDeliveryStatus.FAILED) {
            return "Only failed webhook deliveries can be replayed.";
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return "Webhook delivery is expired.";
        }
        if (replayAttempts >= maxReplayAttempts) {
            return "Webhook delivery replay budget is exhausted.";
        }
        if (nextReplayAt != null && nextReplayAt.isAfter(now)) {
            return "Webhook delivery is cooling down until " + nextReplayAt + ".";
        }
        return "Webhook delivery cannot be replayed.";
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

    public String getProvider() {
        return provider;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public int getReplayAttempts() {
        return replayAttempts;
    }

    public Instant getNextReplayAt() {
        return nextReplayAt;
    }

    public int getMaxReplayAttempts() {
        return maxReplayAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getReplayEligibility() {
        return replayEligibility();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getLastReplayedAt() {
        return lastReplayedAt;
    }

    private String failureReason(String value, String fallback) {
        String reason = value == null || value.isBlank() ? fallback : value;
        return reason.length() <= 1200 ? reason : reason.substring(0, 1200);
    }

    private Instant nextReplayAt() {
        long delaySeconds = Math.min(3600, 60L * (1L << Math.min(replayAttempts, 5)));
        return Instant.now().plusSeconds(delaySeconds);
    }
}
