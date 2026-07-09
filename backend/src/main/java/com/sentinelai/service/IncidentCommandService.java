package com.sentinelai.service;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.Incident;
import com.sentinelai.model.IncidentSeverity;
import com.sentinelai.model.IncidentStatus;
import com.sentinelai.model.IncidentStatusUpdateRequest;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.Signal;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.IncidentRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

@Service
public class IncidentCommandService {

    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;
    private final OperationalEventLogger operationalEventLogger;
    private final BackgroundJobQueueService backgroundJobQueueService;

    public IncidentCommandService(
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            AuditEventRepository auditEventRepository,
            TenantContext tenantContext,
            OperationalEventLogger operationalEventLogger,
            BackgroundJobQueueService backgroundJobQueueService
    ) {
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
        this.operationalEventLogger = operationalEventLogger;
        this.backgroundJobQueueService = backgroundJobQueueService;
    }

    @Transactional
    public List<Incident> activeIncidents() {
        syncFromDeploymentRisk();
        return incidentRepository.findByTenantIdAndStatusNotOrderByUpdatedAtDesc(
                tenantContext.tenantId(),
                IncidentStatus.RESOLVED
        );
    }

    @Transactional
    public Incident updateStatus(long id, IncidentStatusUpdateRequest request) {
        Incident incident = incidentRepository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
        incident.transition(request.status(), request.actor(), request.note());
        audit(
                request.actor(),
                "INCIDENT_" + request.status(),
                incident.getIncidentKey(),
                request.note() == null || request.note().isBlank()
                        ? "Incident status updated."
                        : request.note()
        );
        operationalEventLogger.info("incident.status_changed", java.util.Map.of(
                "incidentKey", incident.getIncidentKey(),
                "serviceName", incident.getServiceName(),
                "status", request.status(),
                "actor", request.actor()
        ));
        Incident saved = incidentRepository.save(incident);
        if (saved.getStatus() != IncidentStatus.RESOLVED) {
            backgroundJobQueueService.enqueueIncidentFollowUp(
                    saved,
                    "Follow up after status changed to " + saved.getStatus().name().toLowerCase().replace("_", " ") + "."
            );
        }
        return saved;
    }

    private void syncFromDeploymentRisk() {
        deploymentRepository.findByTenantId(tenantContext.tenantId()).stream()
                .filter(this::shouldOpenIncident)
                .forEach(this::upsertIncident);
    }

    private boolean shouldOpenIncident(Deployment deployment) {
        RiskAssessment risk = deployment.getRiskAssessment();
        if (risk == null) {
            return false;
        }
        return "production".equalsIgnoreCase(deployment.getEnvironment()) && risk.score() >= 75;
    }

    private void upsertIncident(Deployment deployment) {
        RiskAssessment risk = deployment.getRiskAssessment();
        String incidentKey = "INC-" + deployment.getTenantId() + "-" + deployment.getDeploymentKey();
        IncidentSeverity severity = severity(risk.score());
        String summary = summary(deployment, risk);
        String affectedSystems = affectedSystems(deployment);
        String commanderBrief = commanderBrief(deployment, risk);
        String recommendedAction = recommendedAction(deployment, risk);

        incidentRepository.findByIncidentKeyAndTenantId(incidentKey, tenantContext.tenantId())
                .ifPresentOrElse(
                        incident -> incident.refreshFromRisk(
                                severity,
                                risk.score(),
                                summary,
                                affectedSystems,
                                commanderBrief,
                                recommendedAction
                        ),
                        () -> createIncident(
                                deployment,
                                incidentKey,
                                severity,
                                risk.score(),
                                summary,
                                affectedSystems,
                                commanderBrief,
                                recommendedAction
                        )
                );
    }

    private void createIncident(
            Deployment deployment,
            String incidentKey,
            IncidentSeverity severity,
            int riskScore,
            String summary,
            String affectedSystems,
            String commanderBrief,
            String recommendedAction
    ) {
        Incident incident = new Incident(
                deployment.getTenantId(),
                deployment.getOrganizationName(),
                incidentKey,
                deployment.getId(),
                deployment.getDeploymentKey(),
                deployment.getServiceName(),
                deployment.getOwnerTeam(),
                deployment.getEnvironment(),
                severity,
                riskScore,
                summary,
                affectedSystems,
                commanderBrief,
                recommendedAction,
                Instant.now()
        );
        incident.addTimelineEvent(
                "sentinel-ai",
                "Incident opened by Chief Engineer",
                "Risk score crossed the incident threshold from deployment " + deployment.getDeploymentKey() + "."
        );
        strongestSignals(deployment).forEach(signal -> incident.addTimelineEvent(
                "sentinel-ai",
                signal.title(),
                signal.description()
        ));
        Incident saved = incidentRepository.save(incident);
        backgroundJobQueueService.enqueueIncidentFollowUp(
                saved,
                "Check mitigation progress for " + saved.getIncidentKey() + "."
        );
        audit("sentinel-ai", "INCIDENT_OPENED", incidentKey, summary);
    }

    private IncidentSeverity severity(int riskScore) {
        if (riskScore >= 92) {
            return IncidentSeverity.SEV1;
        }
        if (riskScore >= 84) {
            return IncidentSeverity.SEV2;
        }
        return IncidentSeverity.SEV3;
    }

    private String summary(Deployment deployment, RiskAssessment risk) {
        return deployment.getServiceName() + " has a " + risk.score()
                + "% production risk score tied to " + deployment.getPullRequestTitle() + ".";
    }

    private String affectedSystems(Deployment deployment) {
        if (deployment.getDependencies().isEmpty()) {
            return deployment.getServiceName();
        }
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add(deployment.getServiceName());
        deployment.getDependencies().forEach(joiner::add);
        return joiner.toString();
    }

    private String commanderBrief(Deployment deployment, RiskAssessment risk) {
        String topSignal = strongestSignals(deployment).stream()
                .findFirst()
                .map(signal -> signal.title() + ": " + signal.description())
                .orElse(risk.aiExplanation());
        return "I am treating this as an active engineering incident because "
                + deployment.getOwnerTeam() + " is changing " + deployment.getServiceName()
                + " in production while the evidence shows " + topSignal;
    }

    private String recommendedAction(Deployment deployment, RiskAssessment risk) {
        if (risk.score() >= 92) {
            return "Freeze the release, page " + deployment.getOwnerTeam()
                    + ", prepare rollback, and validate downstream dependencies before the next deploy attempt.";
        }
        if (risk.score() >= 84) {
            return "Move to mitigation: assign an incident commander, run targeted regression checks, and watch "
                    + deployment.getDependencies().stream().findFirst().orElse(deployment.getServiceName()) + " telemetry.";
        }
        return "Investigate before approval: collect CI failures, confirm owner signoff, and keep Sentinel monitoring related signals.";
    }

    private List<Signal> strongestSignals(Deployment deployment) {
        return deployment.getSignals().stream()
                .sorted(Comparator.comparing(Signal::riskWeight).reversed())
                .limit(4)
                .toList();
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
