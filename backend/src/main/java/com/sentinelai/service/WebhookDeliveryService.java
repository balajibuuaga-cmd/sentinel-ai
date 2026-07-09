package com.sentinelai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.GitHubWebhookRequest;
import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.model.WebhookDeliveryStatus;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.repository.WebhookDeliveryRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final BackgroundJobQueueService backgroundJobQueueService;
    private final GitHubWebhookService gitHubWebhookService;
    private final ObjectMapper objectMapper;
    private final TenantContext tenantContext;
    private final OperationalEventLogger operationalEventLogger;
    private final int maxReplayAttempts;
    private final long replayCooldownSeconds;
    private final long retentionDays;

    public WebhookDeliveryService(
            WebhookDeliveryRepository webhookDeliveryRepository,
            BackgroundJobQueueService backgroundJobQueueService,
            GitHubWebhookService gitHubWebhookService,
            ObjectMapper objectMapper,
            TenantContext tenantContext,
            OperationalEventLogger operationalEventLogger,
            @Value("${sentinel.webhooks.replay.max-attempts:3}") int maxReplayAttempts,
            @Value("${sentinel.webhooks.replay.cooldown-seconds:300}") long replayCooldownSeconds,
            @Value("${sentinel.webhooks.delivery.retention-days:30}") long retentionDays
    ) {
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.backgroundJobQueueService = backgroundJobQueueService;
        this.gitHubWebhookService = gitHubWebhookService;
        this.objectMapper = objectMapper;
        this.tenantContext = tenantContext;
        this.operationalEventLogger = operationalEventLogger;
        this.maxReplayAttempts = maxReplayAttempts;
        this.replayCooldownSeconds = replayCooldownSeconds;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public WebhookDelivery recordReceived(
            String provider,
            String externalDeliveryId,
            String eventType,
            String payload,
            String requestId
    ) {
        WebhookDelivery delivery = new WebhookDelivery(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                provider,
                externalDeliveryId,
                eventType,
                payload,
                requestId
        );
        delivery.applyReplayPolicy(maxReplayAttempts, Instant.now().plusSeconds(retentionDays * 24 * 60 * 60));
        return webhookDeliveryRepository.save(delivery);
    }

    @Transactional
    public WebhookDelivery markSucceeded(WebhookDelivery delivery, Deployment deployment) {
        delivery.succeed(deployment.getDeploymentKey());
        return webhookDeliveryRepository.save(delivery);
    }

    @Transactional
    public WebhookDelivery markFailed(WebhookDelivery delivery, String reason) {
        delivery.fail(reason);
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);
        backgroundJobQueueService.enqueueWebhookReplay(saved, saved.getFailureReason());
        operationalEventLogger.warn("webhook.delivery_failed", Map.of(
                "deliveryId", saved.getExternalDeliveryId(),
                "provider", saved.getProvider(),
                "status", saved.getStatus(),
                "reason", saved.getFailureReason()
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> recentForCurrentTenant() {
        return webhookDeliveryRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    @Transactional
    public WebhookDelivery replay(long id) {
        WebhookDelivery delivery = webhookDeliveryRepository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Webhook delivery not found: " + id));
        if (!delivery.replayEligibleAt(Instant.now())) {
            throw new IllegalArgumentException(delivery.replayBlockedReason(Instant.now()));
        }
        delivery.queueReplay(Instant.now().plusSeconds(replayCooldownSeconds));
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);
        backgroundJobQueueService.enqueueWebhookReplay(saved, "Manual webhook replay requested.");
        return saved;
    }

    public WebhookDelivery processReplay(String tenantId, long id) {
        WebhookDelivery delivery = webhookDeliveryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook delivery not found: " + id));
        try {
            GitHubWebhookRequest request = objectMapper.readValue(delivery.getPayload(), GitHubWebhookRequest.class);
            Deployment deployment = gitHubWebhookService.ingestForTenant(
                    request,
                    delivery.getTenantId(),
                    delivery.getOrganizationName(),
                    "webhook-replay"
            );
            delivery.replayed(deployment.getDeploymentKey());
            operationalEventLogger.info("webhook.delivery_replayed", Map.of(
                    "deliveryId", delivery.getExternalDeliveryId(),
                    "provider", delivery.getProvider(),
                    "deploymentKey", deployment.getDeploymentKey()
            ));
        } catch (Exception ex) {
            delivery.replayFailed(ex.getMessage() == null ? "Webhook replay failed." : ex.getMessage());
            operationalEventLogger.warn("webhook.delivery_replay_failed", Map.of(
                    "deliveryId", delivery.getExternalDeliveryId(),
                    "provider", delivery.getProvider(),
                    "reason", delivery.getFailureReason()
            ));
            throw new IllegalStateException(delivery.getFailureReason(), ex);
        }
        return webhookDeliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public long failedCount(String tenantId) {
        return webhookDeliveryRepository.countByTenantIdAndStatus(tenantId, WebhookDeliveryStatus.FAILED);
    }

    @Transactional
    public void cleanupExpiredDeliveries() {
        Instant now = Instant.now();
        webhookDeliveryRepository.findTop50ByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                Set.of(WebhookDeliveryStatus.FAILED, WebhookDeliveryStatus.REPLAY_QUEUED),
                now
        ).forEach(delivery -> {
            delivery.expire();
            webhookDeliveryRepository.save(delivery);
        });
        long deleted = webhookDeliveryRepository.deleteByStatusInAndExpiresAtLessThanEqual(
                Set.of(WebhookDeliveryStatus.SUCCEEDED, WebhookDeliveryStatus.REPLAYED, WebhookDeliveryStatus.EXPIRED),
                now.minusSeconds(24 * 60 * 60)
        );
        if (deleted > 0) {
            operationalEventLogger.info("webhook.delivery_cleanup", Map.of("deleted", deleted));
        }
    }
}
