package com.sentinelai.service;

import com.sentinelai.model.ApprovalDecision;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.EngineeringEvent;
import com.sentinelai.model.EngineeringEventType;
import com.sentinelai.model.MemoryLink;
import com.sentinelai.model.PullRequestDecision;
import com.sentinelai.model.PullRequestReview;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.RiskReason;
import com.sentinelai.model.ServiceProfile;
import com.sentinelai.repository.EngineeringEventRepository;
import com.sentinelai.repository.MemoryLinkRepository;
import com.sentinelai.repository.ServiceProfileRepository;
import com.sentinelai.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class EngineeringMemoryService {

    private final EngineeringEventRepository engineeringEventRepository;
    private final MemoryLinkRepository memoryLinkRepository;
    private final ServiceProfileRepository serviceProfileRepository;
    private final TenantContext tenantContext;

    public EngineeringMemoryService(
            EngineeringEventRepository engineeringEventRepository,
            MemoryLinkRepository memoryLinkRepository,
            ServiceProfileRepository serviceProfileRepository,
            TenantContext tenantContext
    ) {
        this.engineeringEventRepository = engineeringEventRepository;
        this.memoryLinkRepository = memoryLinkRepository;
        this.serviceProfileRepository = serviceProfileRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public void recordDeploymentCreated(Deployment deployment, String source) {
        upsertServiceProfile(deployment);
        seedHistoricalMemoryIfNeeded(deployment);

        EngineeringEvent currentEvent = saveEvent(
                deployment,
                "GITHUB_WEBHOOK".equals(source)
                        ? EngineeringEventType.GITHUB_PR_INGESTED
                        : EngineeringEventType.DEPLOYMENT_CREATED,
                "Deployment review created",
                deployment.getPullRequestTitle() + " was added to Sentinel's release memory."
        );
        link(deployment, currentEvent, "CURRENT_RELEASE", "This is the current release Sentinel is judging.", 88);
        linkSignals(deployment);
    }

    @Transactional
    public void recordDecision(Deployment deployment, ApprovalDecision decision, String actor, String note) {
        EngineeringEventType type = decision == ApprovalDecision.BLOCK
                ? EngineeringEventType.RELEASE_BLOCKED
                : EngineeringEventType.APPROVAL_DECISION;
        EngineeringEvent event = saveEvent(
                deployment,
                type,
                "Deployment " + decision.name().toLowerCase(Locale.US).replace("_", " "),
                actor + " recorded " + decision + " for " + deployment.getDeploymentKey()
                        + ". Note: " + (note == null || note.isBlank() ? "No note provided." : note)
        );
        link(
                deployment,
                event,
                decision == ApprovalDecision.BLOCK ? "HUMAN_BLOCK_DECISION" : "HUMAN_APPROVAL_DECISION",
                "Human release judgment is now part of this service's memory.",
                decision == ApprovalDecision.BLOCK ? 94 : 84
        );
    }

    @Transactional
    public void recordPullRequestReview(PullRequestReview review, Deployment linkedDeployment) {
        EngineeringEvent event = engineeringEventRepository.save(new EngineeringEvent(
                review.getTenantId(),
                review.getOrganizationName(),
                review.getServiceName(),
                review.getOwnerTeam(),
                EngineeringEventType.PR_REVIEW,
                "PR #" + review.getPrNumber() + " reviewed",
                "Sentinel reviewed " + review.getRepository() + " PR #" + review.getPrNumber()
                        + " and recommended " + review.getRecommendation() + " at "
                        + review.getRiskScore() + "% risk.",
                linkedDeployment == null ? null : linkedDeployment.getDeploymentKey(),
                null,
                Instant.now()
        ));

        if (linkedDeployment != null) {
            link(
                    linkedDeployment,
                    event,
                    "AI_ENGINEER_PR_REVIEW",
                    "A pull request review is now connected to this service's release memory.",
                    Math.min(96, 70 + review.getRiskScore() / 4)
            );
        }
    }

    @Transactional
    public void recordPullRequestDecision(PullRequestReview review, PullRequestDecision decision, String actor, String note) {
        engineeringEventRepository.save(new EngineeringEvent(
                review.getTenantId(),
                review.getOrganizationName(),
                review.getServiceName(),
                review.getOwnerTeam(),
                EngineeringEventType.PR_MERGE_DECISION,
                "PR #" + review.getPrNumber() + " " + decision.name().toLowerCase(Locale.US),
                (actor == null || actor.isBlank() ? "release-manager" : actor) + " recorded " + decision
                        + " for " + review.getRepository() + " PR #" + review.getPrNumber()
                        + ". Note: " + (note == null || note.isBlank() ? "No note provided." : note),
                null,
                null,
                Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<MemoryLink> memoryLinksFor(Long deploymentId) {
        return memoryLinkRepository.findTop8ByTenantIdAndDeploymentIdOrderByConfidenceDescCreatedAtDesc(
                tenantContext.tenantId(),
                deploymentId
        );
    }

    private void upsertServiceProfile(Deployment deployment) {
        String criticality = "production".equalsIgnoreCase(deployment.getEnvironment())
                ? "tier-1 production"
                : "pre-production";
        serviceProfileRepository.findByTenantIdAndServiceName(deployment.getTenantId(), deployment.getServiceName())
                .ifPresentOrElse(
                        profile -> {
                            profile.update(
                                    deployment.getOwnerTeam(),
                                    criticality,
                                    deployment.getDependencies(),
                                    Instant.now()
                            );
                            serviceProfileRepository.save(profile);
                        },
                        () -> serviceProfileRepository.save(new ServiceProfile(
                                deployment.getTenantId(),
                                deployment.getOrganizationName(),
                                deployment.getServiceName(),
                                deployment.getOwnerTeam(),
                                criticality,
                                deployment.getDependencies(),
                                Instant.now()
                        ))
                );
    }

    private void seedHistoricalMemoryIfNeeded(Deployment deployment) {
        if (engineeringEventRepository.countByTenantIdAndServiceName(deployment.getTenantId(), deployment.getServiceName()) > 0) {
            return;
        }

        EngineeringEvent priorAnomaly = engineeringEventRepository.save(new EngineeringEvent(
                deployment.getTenantId(),
                deployment.getOrganizationName(),
                deployment.getServiceName(),
                deployment.getOwnerTeam(),
                EngineeringEventType.INCIDENT_PATTERN,
                "Prior release anomaly",
                deployment.getOwnerTeam() + " previously shipped a release with correlated runtime risk.",
                null,
                null,
                Instant.now().minusSeconds(92L * 24 * 60 * 60)
        ));
        link(deployment, priorAnomaly, "HISTORICAL_INCIDENT_MEMORY", "This service has a prior anomaly pattern Sentinel should remember.", 86);

        EngineeringEvent dependencyPressure = engineeringEventRepository.save(new EngineeringEvent(
                deployment.getTenantId(),
                deployment.getOrganizationName(),
                deployment.getServiceName(),
                deployment.getOwnerTeam(),
                EngineeringEventType.DEPENDENCY_RISK,
                "Dependency pressure",
                deployment.getServiceName() + " sits near " + firstDependency(deployment) + ", increasing blast radius during release.",
                null,
                null,
                Instant.now().minusSeconds(71L * 24 * 60 * 60)
        ));
        link(deployment, dependencyPressure, "DEPENDENCY_MEMORY", "The service dependency graph increases release blast radius.", 80);
    }

    private void linkSignals(Deployment deployment) {
        RiskAssessment assessment = deployment.getRiskAssessment();
        if (assessment == null) {
            return;
        }

        for (RiskReason reason : assessment.reasons()) {
            EngineeringEventType eventType = eventTypeFor(reason);
            if (eventType == null) {
                continue;
            }

            EngineeringEvent event = saveEvent(
                    deployment,
                    eventType,
                    reason.category(),
                    reason.evidence()
            );
            link(
                    deployment,
                    event,
                    eventType.name(),
                    "Sentinel linked this evidence to the current release judgment.",
                    Math.min(96, 68 + reason.impact())
            );
        }
    }

    private EngineeringEventType eventTypeFor(RiskReason reason) {
        String category = reason.category().toLowerCase(Locale.US);
        String evidence = reason.evidence().toLowerCase(Locale.US);
        if (category.contains("incident") || evidence.contains("outage")) {
            return EngineeringEventType.INCIDENT_PATTERN;
        }
        if (evidence.contains("migration") || evidence.contains("database")) {
            return EngineeringEventType.DATABASE_MIGRATION;
        }
        if (category.contains("build") || category.contains("test") || evidence.contains("ci")) {
            return EngineeringEventType.CI_SIGNAL;
        }
        if (category.contains("dependency") || evidence.contains("dependency")) {
            return EngineeringEventType.DEPENDENCY_RISK;
        }
        return null;
    }

    private EngineeringEvent saveEvent(
            Deployment deployment,
            EngineeringEventType type,
            String title,
            String details
    ) {
        return engineeringEventRepository.save(new EngineeringEvent(
                deployment.getTenantId(),
                deployment.getOrganizationName(),
                deployment.getServiceName(),
                deployment.getOwnerTeam(),
                type,
                title,
                details,
                deployment.getDeploymentKey(),
                deployment.getCommitSha(),
                Instant.now()
        ));
    }

    private void link(
            Deployment deployment,
            EngineeringEvent event,
            String patternType,
            String reason,
            int confidence
    ) {
        if (deployment.getId() == null || event.getId() == null
                || memoryLinkRepository.existsByTenantIdAndDeploymentIdAndEngineeringEvent_Id(
                deployment.getTenantId(),
                deployment.getId(),
                event.getId()
        )) {
            return;
        }
        memoryLinkRepository.save(new MemoryLink(
                deployment.getTenantId(),
                deployment.getOrganizationName(),
                deployment.getId(),
                event,
                patternType,
                reason,
                confidence,
                Instant.now()
        ));
    }

    private String firstDependency(Deployment deployment) {
        return deployment.getDependencies().isEmpty()
                ? "a core dependency"
                : deployment.getDependencies().get(0);
    }
}
