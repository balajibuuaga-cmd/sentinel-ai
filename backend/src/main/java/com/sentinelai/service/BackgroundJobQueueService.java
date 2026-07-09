package com.sentinelai.service;

import com.sentinelai.model.BackgroundJob;
import com.sentinelai.model.BackgroundJobStatus;
import com.sentinelai.model.BackgroundJobType;
import com.sentinelai.model.Incident;
import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.model.operator.BackgroundJobSummary;
import com.sentinelai.repository.BackgroundJobRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BackgroundJobQueueService {

    private final BackgroundJobRepository repository;
    private final TenantContext tenantContext;

    public BackgroundJobQueueService(BackgroundJobRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public BackgroundJob enqueueProviderSyncRetry(IntegrationConnection connection, String reason) {
        return repository.save(new BackgroundJob(
                connection.getTenantId(),
                connection.getOrganizationName(),
                BackgroundJobType.PROVIDER_SYNC_RETRY,
                "integration_connection",
                connection.getId(),
                connection.getProvider().name(),
                reason == null || reason.isBlank() ? "Retry provider sync." : reason,
                4,
                Instant.now().plusSeconds(60)
        ));
    }

    @Transactional
    public BackgroundJob enqueueIncidentFollowUp(Incident incident, String reason) {
        return repository.save(new BackgroundJob(
                incident.getTenantId(),
                incident.getOrganizationName(),
                BackgroundJobType.INCIDENT_FOLLOW_UP,
                "incident",
                incident.getId(),
                incident.getIncidentKey(),
                reason == null || reason.isBlank() ? "Follow up on active incident." : reason,
                3,
                Instant.now().plusSeconds(300)
        ));
    }

    @Transactional
    public BackgroundJob enqueueWebhookReplay(WebhookDelivery delivery, String reason) {
        return repository.save(new BackgroundJob(
                delivery.getTenantId(),
                delivery.getOrganizationName(),
                BackgroundJobType.WEBHOOK_REPLAY,
                "webhook_delivery",
                delivery.getId(),
                delivery.getProvider() + ":" + delivery.getExternalDeliveryId(),
                payload(reason, "Replay webhook delivery."),
                5,
                delivery.getNextReplayAt() == null ? Instant.now().plusSeconds(30) : delivery.getNextReplayAt()
        ));
    }

    @Transactional(readOnly = true)
    public List<BackgroundJob> recentForCurrentTenant() {
        return repository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    @Transactional
    public BackgroundJob retry(long id) {
        BackgroundJob job = repository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Background job not found: " + id));
        job.retryNow();
        return repository.save(job);
    }

    @Transactional(readOnly = true)
    public BackgroundJobSummary summaryForCurrentTenant() {
        return summary(tenantContext.tenantId());
    }

    @Transactional(readOnly = true)
    public BackgroundJobSummary summary(String tenantId) {
        return new BackgroundJobSummary(
                repository.countByTenantIdAndStatus(tenantId, BackgroundJobStatus.QUEUED),
                repository.countByTenantIdAndStatus(tenantId, BackgroundJobStatus.RUNNING),
                repository.countByTenantIdAndStatus(tenantId, BackgroundJobStatus.FAILED),
                repository.countByTenantIdAndStatus(tenantId, BackgroundJobStatus.SUCCEEDED)
        );
    }

    private String payload(String value, String fallback) {
        String payload = value == null || value.isBlank() ? fallback : value;
        return payload.length() <= 1200 ? payload : payload.substring(0, 1200);
    }
}
