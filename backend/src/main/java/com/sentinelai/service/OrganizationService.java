package com.sentinelai.service;

import com.sentinelai.model.IntegrationStatus;
import com.sentinelai.model.intelligence.OnboardingStep;
import com.sentinelai.model.intelligence.OrganizationProfile;
import com.sentinelai.repository.ArchitectureServiceRepository;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.IntegrationConnectionRepository;
import com.sentinelai.repository.PullRequestReviewRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final TenantContext tenantContext;
    private final DeploymentRepository deploymentRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final ArchitectureServiceRepository architectureServiceRepository;
    private final AuditEventRepository auditEventRepository;
    private final IntegrationConnectionRepository integrationConnectionRepository;

    public OrganizationService(
            TenantContext tenantContext,
            DeploymentRepository deploymentRepository,
            PullRequestReviewRepository pullRequestReviewRepository,
            ArchitectureServiceRepository architectureServiceRepository,
            AuditEventRepository auditEventRepository,
            IntegrationConnectionRepository integrationConnectionRepository
    ) {
        this.tenantContext = tenantContext;
        this.deploymentRepository = deploymentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.architectureServiceRepository = architectureServiceRepository;
        this.auditEventRepository = auditEventRepository;
        this.integrationConnectionRepository = integrationConnectionRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationProfile current() {
        String tenantId = tenantContext.tenantId();
        int deployments = deploymentRepository.findByTenantId(tenantId).size();
        int prReviews = pullRequestReviewRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId).size();
        int architectureServices = architectureServiceRepository.findByTenantId(tenantId).size();
        int auditEvents = auditEventRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).size();
        int connectedIntegrations = (int) integrationConnectionRepository.countByTenantIdAndStatus(tenantId, IntegrationStatus.CONNECTED);
        List<OnboardingStep> steps = List.of(
                new OnboardingStep("Workspace secured", true, "JWT carries tenant and role claims."),
                new OnboardingStep("Integrations connected", connectedIntegrations >= 3, connectedIntegrations + "/3 production integrations connected."),
                new OnboardingStep("Release signals connected", deployments > 0, deployments + " deployment reviews in this workspace."),
                new OnboardingStep("AI Engineer activated", prReviews > 0, prReviews + " pull request reviews recorded."),
                new OnboardingStep("Architecture imported", architectureServices > 0, architectureServices + " services mapped."),
                new OnboardingStep("Audit trail live", auditEvents > 0, auditEvents + " recent tenant-scoped decisions.")
        );
        long complete = steps.stream().filter(OnboardingStep::complete).count();
        String status = complete == steps.size()
                ? "Production-ready demo workspace"
                : "Onboarding in progress";

        return new OrganizationProfile(
                tenantId,
                tenantContext.organizationName(),
                status,
                deployments,
                prReviews,
                architectureServices,
                auditEvents,
                connectedIntegrations,
                steps
        );
    }
}
