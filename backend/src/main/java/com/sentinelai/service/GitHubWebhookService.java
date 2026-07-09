package com.sentinelai.service;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.DeploymentStatus;
import com.sentinelai.model.GitHubWebhookRequest;
import com.sentinelai.model.Signal;
import com.sentinelai.model.SignalType;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.model.AuditEvent;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.observability.OperationalMetrics;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubWebhookService {

    private final DeploymentRepository deploymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final EngineeringMemoryService engineeringMemoryService;
    private final TenantContext tenantContext;
    private final OperationalMetrics operationalMetrics;
    private final OperationalEventLogger operationalEventLogger;

    public GitHubWebhookService(
            DeploymentRepository deploymentRepository,
            AuditEventRepository auditEventRepository,
            RiskAnalysisService riskAnalysisService,
            EngineeringMemoryService engineeringMemoryService,
            TenantContext tenantContext,
            OperationalMetrics operationalMetrics,
            OperationalEventLogger operationalEventLogger
    ) {
        this.deploymentRepository = deploymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.riskAnalysisService = riskAnalysisService;
        this.engineeringMemoryService = engineeringMemoryService;
        this.tenantContext = tenantContext;
        this.operationalMetrics = operationalMetrics;
        this.operationalEventLogger = operationalEventLogger;
    }

    @Transactional
    public Deployment ingest(GitHubWebhookRequest request) {
        return ingestForTenant(request, tenantContext.tenantId(), tenantContext.organizationName(), null);
    }

    @Transactional
    public Deployment ingestForTenant(
            GitHubWebhookRequest request,
            String tenantId,
            String organizationName,
            String defaultActor
    ) {
        validate(request);
        List<String> changedFiles = request.changedFiles() == null ? List.of() : request.changedFiles();
        List<String> dependencies = request.dependencies() == null ? List.of() : request.dependencies();
        List<Signal> signals = new ArrayList<>();
        String actor = request.actor() == null || request.actor().isBlank()
                ? defaultActor == null || defaultActor.isBlank() ? "github-webhook" : defaultActor
                : request.actor();

        signals.add(new Signal(
                SignalType.GITHUB,
                "Pull request received",
                request.repository() + " changed " + changedFiles.size() + " files.",
                Math.min(24, 6 + changedFiles.size() * 2)
        ));

        if (changedFiles.stream().anyMatch(file -> file.toLowerCase().contains("migration"))) {
            signals.add(new Signal(
                    SignalType.GITHUB,
                    "Database migration detected",
                    "Changed files include a migration path.",
                    18
            ));
        }

        if (!"success".equalsIgnoreCase(request.ciStatus())) {
            signals.add(new Signal(
                    SignalType.CI,
                    "CI is not green",
                    "GitHub reported CI status: " + (request.ciStatus() == null ? "unknown" : request.ciStatus()) + ".",
                    20
            ));
        }

        Deployment deployment = new Deployment(
                0,
                tenantId,
                organizationName,
                "GH-" + Instant.now().toEpochMilli(),
                request.serviceName(),
                request.ownerTeam(),
                request.environment(),
                request.commitSha(),
                request.pullRequestTitle(),
                dependencies,
                signals,
                Instant.now(),
                DeploymentStatus.READY_FOR_REVIEW
        );
        deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));

        Deployment saved = deploymentRepository.save(deployment);
        engineeringMemoryService.recordDeploymentCreated(saved, "GITHUB_WEBHOOK");
        auditEventRepository.save(new AuditEvent(
                tenantId,
                organizationName,
                actor,
                "GITHUB_WEBHOOK_INGESTED",
                saved.getDeploymentKey(),
                "Created deployment review from " + request.repository() + " at commit " + request.commitSha() + ". requestId=" + RequestContext.requestId(),
                Instant.now()
        ));
        operationalMetrics.webhookIngested("github");
        operationalMetrics.deploymentReviewCreated("GITHUB_WEBHOOK");
        operationalEventLogger.info("github.webhook_ingested", java.util.Map.of(
                "repository", request.repository(),
                "deploymentKey", saved.getDeploymentKey(),
                "commitSha", request.commitSha(),
                "actor", actor
        ));
        return saved;
    }

    private void validate(GitHubWebhookRequest request) {
        require(request.repository(), "repository");
        require(request.serviceName(), "serviceName");
        require(request.ownerTeam(), "ownerTeam");
        require(request.environment(), "environment");
        require(request.commitSha(), "commitSha");
        require(request.pullRequestTitle(), "pullRequestTitle");
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitHub webhook field is required: " + field);
        }
    }
}
