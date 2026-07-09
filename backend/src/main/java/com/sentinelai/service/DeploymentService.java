package com.sentinelai.service;

import com.sentinelai.model.ApprovalDecision;
import com.sentinelai.model.ApprovalRequest;
import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.DeploymentStatus;
import com.sentinelai.model.Signal;
import com.sentinelai.model.SignalType;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.observability.OperationalMetrics;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.security.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DeploymentService {

    private final RiskAnalysisService riskAnalysisService;
    private final EngineeringMemoryService engineeringMemoryService;
    private final DeploymentRepository deploymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;
    private final OperationalMetrics operationalMetrics;
    private final OperationalEventLogger operationalEventLogger;

    public DeploymentService(
            RiskAnalysisService riskAnalysisService,
            EngineeringMemoryService engineeringMemoryService,
            DeploymentRepository deploymentRepository,
            AuditEventRepository auditEventRepository,
            TenantContext tenantContext,
            OperationalMetrics operationalMetrics,
            OperationalEventLogger operationalEventLogger
    ) {
        this.riskAnalysisService = riskAnalysisService;
        this.engineeringMemoryService = engineeringMemoryService;
        this.deploymentRepository = deploymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
        this.operationalMetrics = operationalMetrics;
        this.operationalEventLogger = operationalEventLogger;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (deploymentRepository.count() > 0) {
            return;
        }

        addDeployment(new Deployment(
                1,
                TenantContext.DEFAULT_TENANT_ID,
                TenantContext.DEFAULT_ORGANIZATION_NAME,
                "DEP-1234",
                "payment-api",
                "Payments Platform",
                "production",
                "4af92e1",
                "Add settlement retry flow and update account ledger query",
                List.of("checkout-service", "billing-service", "fraud-screening", "customer-ledger"),
                List.of(
                        new Signal(SignalType.GITHUB, "High-risk code path",
                                "Payment authorization and settlement code changed in the same PR.", 18),
                        new Signal(SignalType.CI, "Failed integration tests",
                                "Checkout regression suite has 3 failing tests.", 22),
                        new Signal(SignalType.INCIDENT_HISTORY, "Similar outage found",
                                "A ledger query change caused a payment outage 43 days ago.", 20),
                        new Signal(SignalType.LOGS, "Runtime warning",
                                "Database timeout rate increased 31% in the last hour.", 16),
                        new Signal(SignalType.JIRA, "Urgent bug attached",
                                "Release includes a high-priority defect fix with incomplete QA signoff.", 12)
                ),
                Instant.now().minusSeconds(5400),
                DeploymentStatus.READY_FOR_REVIEW
        ));

        addDeployment(new Deployment(
                2,
                TenantContext.DEFAULT_TENANT_ID,
                TenantContext.DEFAULT_ORGANIZATION_NAME,
                "DEP-1235",
                "profile-service",
                "Identity Experience",
                "staging",
                "8bc17fd",
                "Update user avatar upload limits",
                List.of("web-app"),
                List.of(
                        new Signal(SignalType.GITHUB, "Small surface area",
                                "Only validation constants and upload copy changed.", 5),
                        new Signal(SignalType.CI, "Tests passing",
                                "Unit and API tests passed with stable coverage.", 0),
                        new Signal(SignalType.LOGS, "No anomaly",
                                "No related production errors in the last 24 hours.", 0)
                ),
                Instant.now().minusSeconds(2600),
                DeploymentStatus.READY_FOR_REVIEW
        ));

        addDeployment(new Deployment(
                3,
                TenantContext.DEFAULT_TENANT_ID,
                TenantContext.DEFAULT_ORGANIZATION_NAME,
                "DEP-1236",
                "inventory-sync",
                "Commerce Operations",
                "production",
                "91ed04a",
                "Change warehouse sync job and add stock reservation migration",
                List.of("product-api", "checkout-service", "warehouse-adapter"),
                List.of(
                        new Signal(SignalType.GITHUB, "Database migration",
                                "Migration touches stock_reservation and warehouse_snapshot tables.", 18),
                        new Signal(SignalType.CI, "Coverage dropped",
                                "Changed package coverage dropped from 82% to 61%.", 15),
                        new Signal(SignalType.SERVICE_DEPENDENCY, "Checkout dependency",
                                "Checkout reads inventory availability before payment.", 14),
                        new Signal(SignalType.JIRA, "Scope changed late",
                                "Two linked tickets were added after code review started.", 10)
                ),
                Instant.now().minusSeconds(1200),
                DeploymentStatus.READY_FOR_REVIEW
        ));

        audit("system", "SEED_DATA", "sentinel-ai", "Loaded sample deployment intelligence data.");
    }

    @Transactional(readOnly = true)
    public List<Deployment> findAll() {
        return deploymentRepository.findByTenantId(tenantContext.tenantId()).stream()
                .sorted(Comparator.comparing(Deployment::getCreatedAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> findById(long id) {
        return deploymentRepository.findByIdAndTenantId(id, tenantContext.tenantId());
    }

    @Transactional
    public Deployment analyze(long id) {
        Deployment deployment = requireDeployment(id);
        deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));
        Deployment saved = deploymentRepository.save(deployment);
        audit(
                "sentinel-ai",
                "RISK_ANALYZED",
                deployment.getDeploymentKey(),
                "Generated risk score " + deployment.getRiskAssessment().score()
                        + "% for " + deployment.getServiceName()
        );
        return saved;
    }

    @Transactional
    public Deployment decide(long id, ApprovalRequest request) {
        Deployment deployment = requireDeployment(id);
        if (deployment.getRiskAssessment() == null) {
            deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));
        }

        if (request.decision() == ApprovalDecision.APPROVE) {
            deployment.setStatus(DeploymentStatus.APPROVED);
        } else if (request.decision() == ApprovalDecision.BLOCK) {
            deployment.setStatus(DeploymentStatus.BLOCKED);
        } else {
            deployment.setStatus(DeploymentStatus.READY_FOR_REVIEW);
        }

        audit(
                request.approver(),
                "DEPLOYMENT_" + request.decision(),
                deployment.getDeploymentKey(),
                request.note() == null || request.note().isBlank()
                        ? "No note provided."
                        : request.note()
        );

        Deployment saved = deploymentRepository.save(deployment);
        engineeringMemoryService.recordDecision(saved, request.decision(), request.approver(), request.note());
        operationalMetrics.approvalDecision(request.decision());
        operationalEventLogger.info("deployment.approval_decision", java.util.Map.of(
                "actor", request.approver(),
                "decision", request.decision(),
                "deploymentKey", saved.getDeploymentKey(),
                "riskScore", saved.getRiskAssessment() == null ? "" : saved.getRiskAssessment().score()
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> auditEvents() {
        return auditEventRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    private void addDeployment(Deployment deployment) {
        deployment.setRiskAssessment(riskAnalysisService.analyze(deployment));
        Deployment saved = deploymentRepository.save(deployment);
        engineeringMemoryService.recordDeploymentCreated(saved, "SEED_DATA");
        operationalMetrics.deploymentReviewCreated("SEED_DATA");
    }

    private Deployment requireDeployment(long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + id));
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
