package com.sentinelai.service;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.CiSignalRequest;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.DeploymentStatus;
import com.sentinelai.model.JiraSignalRequest;
import com.sentinelai.model.Signal;
import com.sentinelai.model.SignalType;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.observability.OperationalMetrics;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EngineeringSignalIngestionService {

    private final DeploymentRepository deploymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final EngineeringMemoryService engineeringMemoryService;
    private final TenantContext tenantContext;
    private final OperationalMetrics operationalMetrics;
    private final OperationalEventLogger operationalEventLogger;

    public EngineeringSignalIngestionService(
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
    public Deployment ingestCi(CiSignalRequest request) {
        List<Signal> signals = ciSignals(request);
        Deployment deployment = findOrCreateDeployment(
                request.serviceName(),
                request.ownerTeam(),
                request.environment(),
                request.commitSha(),
                request.pipelineName() + " reported " + request.status(),
                request.dependencies(),
                signals
        );
        if (deployment.getId() != null) {
            deployment.addSignals(signals);
        }
        deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));
        Deployment saved = deploymentRepository.save(deployment);
        engineeringMemoryService.recordDeploymentCreated(saved, "CI_SIGNAL");
        audit(
                actorOrDefault(request.actor(), request.provider()),
                "CI_SIGNAL_INGESTED",
                saved.getDeploymentKey(),
                request.provider() + " reported " + request.status() + " for " + request.pipelineName() + "."
        );
        operationalMetrics.deploymentReviewCreated("CI_SIGNAL");
        operationalEventLogger.info("ci.signal_ingested", java.util.Map.of(
                "provider", request.provider(),
                "deploymentKey", saved.getDeploymentKey(),
                "pipelineName", request.pipelineName(),
                "status", request.status()
        ));
        return saved;
    }

    @Transactional
    public Deployment ingestJira(JiraSignalRequest request) {
        List<Signal> signals = jiraSignals(request);
        Deployment deployment = findOrCreateDeployment(
                request.serviceName(),
                request.ownerTeam(),
                request.environment(),
                request.commitSha() == null || request.commitSha().isBlank() ? "jira-" + request.issueKey().toLowerCase(Locale.US) : request.commitSha(),
                request.issueKey() + ": " + request.summary(),
                request.dependencies(),
                signals
        );
        if (deployment.getId() != null) {
            deployment.addSignals(signals);
        }
        deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));
        Deployment saved = deploymentRepository.save(deployment);
        engineeringMemoryService.recordDeploymentCreated(saved, "JIRA_SIGNAL");
        audit(
                actorOrDefault(request.actor(), "jira"),
                "JIRA_SIGNAL_INGESTED",
                saved.getDeploymentKey(),
                request.issueKey() + " " + request.priority() + " " + request.issueType() + " linked to " + request.serviceName() + "."
        );
        operationalMetrics.deploymentReviewCreated("JIRA_SIGNAL");
        operationalEventLogger.info("jira.signal_ingested", java.util.Map.of(
                "issueKey", request.issueKey(),
                "deploymentKey", saved.getDeploymentKey(),
                "priority", request.priority(),
                "status", request.status()
        ));
        return saved;
    }

    private Deployment findOrCreateDeployment(
            String serviceName,
            String ownerTeam,
            String environment,
            String commitSha,
            String title,
            List<String> dependencies,
            List<Signal> initialSignals
    ) {
        return deploymentRepository.findByTenantId(tenantContext.tenantId()).stream()
                .filter(deployment -> deployment.getServiceName().equalsIgnoreCase(serviceName))
                .filter(deployment -> deployment.getCommitSha().equalsIgnoreCase(commitSha))
                .max(Comparator.comparing(Deployment::getCreatedAt))
                .orElseGet(() -> new Deployment(
                        0,
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        "SIG-" + Instant.now().toEpochMilli(),
                        serviceName,
                        ownerTeam,
                        environment,
                        commitSha,
                        title,
                        dependencies == null ? List.of() : dependencies,
                        initialSignals,
                        Instant.now(),
                        DeploymentStatus.READY_FOR_REVIEW
                ));
    }

    private List<Signal> ciSignals(CiSignalRequest request) {
        List<Signal> signals = new ArrayList<>();
        String status = request.status().toLowerCase(Locale.US);
        int failedTests = request.failedTests() == null ? 0 : request.failedTests();
        int coverageDelta = request.coverageDelta() == null ? 0 : request.coverageDelta();

        signals.add(new Signal(
                SignalType.CI,
                request.provider() + " " + request.pipelineName(),
                "Pipeline finished with status " + request.status()
                        + optionalUrl(request.buildUrl()) + ".",
                ciStatusWeight(status)
        ));

        if (failedTests > 0) {
            signals.add(new Signal(
                    SignalType.CI,
                    "Failed test suites",
                    failedTests + " tests failed"
                            + listDetail(request.failedSuites())
                            + ".",
                    Math.min(24, 8 + failedTests * 2)
            ));
        }

        if (coverageDelta <= -8) {
            signals.add(new Signal(
                    SignalType.CI,
                    "Coverage regression",
                    "Changed-code coverage moved " + coverageDelta + " percentage points.",
                    Math.min(18, Math.abs(coverageDelta))
            ));
        }

        return signals;
    }

    private List<Signal> jiraSignals(JiraSignalRequest request) {
        List<Signal> signals = new ArrayList<>();
        String priority = request.priority().toLowerCase(Locale.US);
        String issueType = request.issueType().toLowerCase(Locale.US);
        String status = request.status().toLowerCase(Locale.US);

        signals.add(new Signal(
                SignalType.JIRA,
                request.issueKey() + " " + request.priority(),
                request.summary() + " is " + request.status() + ".",
                jiraPriorityWeight(priority)
        ));

        if (issueType.contains("incident") || issueType.contains("bug")) {
            signals.add(new Signal(
                    SignalType.INCIDENT_HISTORY,
                    "Incident-linked work",
                    request.issueKey() + " is a " + request.issueType() + " attached to this release.",
                    issueType.contains("incident") ? 20 : 12
            ));
        }

        if (!status.contains("done") && !status.contains("closed") && !status.contains("resolved")) {
            signals.add(new Signal(
                    SignalType.JIRA,
                    "Incomplete work item",
                    request.issueKey() + " is not resolved before release.",
                    12
            ));
        }

        if (request.labels() != null && request.labels().stream().anyMatch(label -> label.equalsIgnoreCase("hotfix") || label.equalsIgnoreCase("customer-impact"))) {
            signals.add(new Signal(
                    SignalType.JIRA,
                    "Customer-impacting change",
                    "Labels include hotfix or customer-impact context.",
                    14
            ));
        }

        return signals;
    }

    private int ciStatusWeight(String status) {
        if (status.contains("failure") || status.contains("failed")) {
            return 24;
        }
        if (status.contains("cancel") || status.contains("timed")) {
            return 16;
        }
        if (status.contains("pending") || status.contains("running")) {
            return 10;
        }
        return 0;
    }

    private int jiraPriorityWeight(String priority) {
        if (priority.contains("blocker") || priority.contains("critical")) {
            return 22;
        }
        if (priority.contains("high")) {
            return 16;
        }
        if (priority.contains("medium")) {
            return 9;
        }
        return 4;
    }

    private String optionalUrl(String url) {
        return url == null || url.isBlank() ? "" : " at " + url;
    }

    private String listDetail(List<String> values) {
        return values == null || values.isEmpty() ? "" : " across " + String.join(", ", values);
    }

    private String actorOrDefault(String actor, String fallback) {
        return actor == null || actor.isBlank() ? fallback : actor;
    }

    private void audit(String actor, String action, String target, String details) {
        auditEventRepository.save(new AuditEvent(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                actor,
                action,
                target,
                details + " requestId=" + RequestContext.requestId(),
                Instant.now()
        ));
    }
}
