package com.sentinelai.service;

import com.sentinelai.RuntimeModeService;
import com.sentinelai.model.BackgroundJob;
import com.sentinelai.model.BackgroundJobStatus;
import com.sentinelai.model.Incident;
import com.sentinelai.model.IncidentStatus;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.repository.BackgroundJobRepository;
import com.sentinelai.repository.IncidentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class BackgroundJobWorkerService {

    private final BackgroundJobRepository backgroundJobRepository;
    private final IntegrationConnectionService integrationConnectionService;
    private final IncidentRepository incidentRepository;
    private final OperationalEventLogger operationalEventLogger;
    private final WebhookDeliveryService webhookDeliveryService;
    private final RuntimeModeService runtimeModeService;

    public BackgroundJobWorkerService(
            BackgroundJobRepository backgroundJobRepository,
            IntegrationConnectionService integrationConnectionService,
            IncidentRepository incidentRepository,
            OperationalEventLogger operationalEventLogger,
            WebhookDeliveryService webhookDeliveryService,
            RuntimeModeService runtimeModeService
    ) {
        this.backgroundJobRepository = backgroundJobRepository;
        this.integrationConnectionService = integrationConnectionService;
        this.incidentRepository = incidentRepository;
        this.operationalEventLogger = operationalEventLogger;
        this.webhookDeliveryService = webhookDeliveryService;
        this.runtimeModeService = runtimeModeService;
    }

    @Scheduled(fixedDelayString = "${sentinel.jobs.worker-delay-ms:60000}")
    public void processDueJobs() {
        if (!runtimeModeService.workerEnabled()) {
            return;
        }
        backgroundJobRepository.findTop10ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                BackgroundJobStatus.QUEUED,
                Instant.now()
        ).forEach(this::process);
    }

    @Scheduled(fixedDelayString = "${sentinel.webhooks.delivery.cleanup-delay-ms:3600000}")
    public void cleanupWebhookDeliveries() {
        if (!runtimeModeService.workerEnabled()) {
            return;
        }
        webhookDeliveryService.cleanupExpiredDeliveries();
    }

    private void process(BackgroundJob job) {
        job.start();
        backgroundJobRepository.save(job);
        try {
            switch (job.getJobType()) {
                case PROVIDER_SYNC_RETRY -> integrationConnectionService.syncForJob(job.getTenantId(), job.getTargetId());
                case INCIDENT_FOLLOW_UP -> followUpIncident(job);
                case WEBHOOK_REPLAY -> webhookDeliveryService.processReplay(job.getTenantId(), job.getTargetId());
            }
            job.succeed();
            operationalEventLogger.info("background_job.succeeded", Map.of(
                    "jobId", job.getId(),
                    "jobType", job.getJobType(),
                    "attempts", job.getAttempts()
            ));
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Background job failed." : ex.getMessage();
            job.fail(message, nextRetryAt(job));
            operationalEventLogger.warn("background_job.failed", Map.of(
                    "jobId", job.getId(),
                    "jobType", job.getJobType(),
                    "attempts", job.getAttempts(),
                    "status", job.getStatus(),
                    "message", message
            ));
        }
        backgroundJobRepository.save(job);
    }

    private void followUpIncident(BackgroundJob job) {
        Incident incident = incidentRepository.findByIdAndTenantId(job.getTargetId(), job.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + job.getTargetId()));
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            incident.addTimelineEvent(
                    "sentinel-jobs",
                    "Automated follow-up skipped",
                    "Incident is already resolved."
            );
            incidentRepository.save(incident);
            return;
        }
        incident.addTimelineEvent(
                "sentinel-jobs",
                "Automated mitigation follow-up",
                "Sentinel checked the incident queue and kept this item active for operator review. " + job.getPayload()
        );
        incidentRepository.save(incident);
    }

    private Instant nextRetryAt(BackgroundJob job) {
        long delaySeconds = Math.min(1800, 60L * (1L << Math.min(job.getAttempts(), 5)));
        return Instant.now().plusSeconds(delaySeconds);
    }
}
