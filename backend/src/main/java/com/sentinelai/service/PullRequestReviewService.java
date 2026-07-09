package com.sentinelai.service;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.PullRequestDecisionRequest;
import com.sentinelai.model.PullRequestRecommendation;
import com.sentinelai.model.PullRequestReview;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.PullRequestReviewRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.service.ai.ChiefEngineerReasoningProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PullRequestReviewService {

    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final DeploymentRepository deploymentRepository;
    private final EngineeringMemoryService engineeringMemoryService;
    private final TenantContext tenantContext;
    private final ChiefEngineerReasoningProvider reasoningProvider;

    public PullRequestReviewService(
            PullRequestReviewRepository pullRequestReviewRepository,
            DeploymentRepository deploymentRepository,
            EngineeringMemoryService engineeringMemoryService,
            TenantContext tenantContext,
            ChiefEngineerReasoningProvider reasoningProvider
    ) {
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.deploymentRepository = deploymentRepository;
        this.engineeringMemoryService = engineeringMemoryService;
        this.tenantContext = tenantContext;
        this.reasoningProvider = reasoningProvider;
    }

    @Transactional(readOnly = true)
    public List<PullRequestReview> findAll() {
        return pullRequestReviewRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    @Transactional(readOnly = true)
    public Optional<PullRequestReview> findById(long id) {
        return pullRequestReviewRepository.findByIdAndTenantId(id, tenantContext.tenantId());
    }

    @Transactional(readOnly = true)
    public Optional<PullRequestReview> latest() {
        return pullRequestReviewRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    @Transactional
    public PullRequestReview simulate(PullRequestReviewRequest request) {
        Deployment linkedDeployment = deploymentRepository.findByTenantId(tenantContext.tenantId()).stream()
                .filter(deployment -> deployment.getServiceName().equalsIgnoreCase(request.serviceName()))
                .max(Comparator.comparing(Deployment::getCreatedAt))
                .orElse(null);

        int riskScore = score(request, linkedDeployment);
        PullRequestRecommendation recommendation = recommendationFor(riskScore, request.ciStatus());
        PullRequestReview review = new PullRequestReview(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                request.repository(),
                request.prNumber(),
                request.title(),
                request.author(),
                request.serviceName(),
                request.ownerTeam(),
                request.ciStatus(),
                request.changedFiles(),
                linkedDeployment == null ? null : linkedDeployment.getId(),
                riskScore,
                recommendation,
                reasoningProvider.pullRequestExplanation(request, linkedDeployment, riskScore, recommendation),
                Instant.now()
        );

        PullRequestReview saved = pullRequestReviewRepository.save(review);
        engineeringMemoryService.recordPullRequestReview(saved, linkedDeployment);
        return saved;
    }

    @Transactional
    public PullRequestReview decide(long id, PullRequestDecisionRequest request) {
        PullRequestReview review = pullRequestReviewRepository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Pull request review not found: " + id));
        review.decide(request.decision(), request.note());
        PullRequestReview saved = pullRequestReviewRepository.save(review);
        engineeringMemoryService.recordPullRequestDecision(saved, request.decision(), request.actor(), request.note());
        return saved;
    }

    private int score(PullRequestReviewRequest request, Deployment linkedDeployment) {
        int score = 12;
        String title = request.title().toLowerCase(Locale.US);
        List<String> files = request.changedFiles();

        score += Math.min(28, files.size() * 4);
        if (!"success".equalsIgnoreCase(request.ciStatus())) {
            score += 24;
        }
        if (files.stream().anyMatch(file -> file.toLowerCase(Locale.US).contains("migration"))) {
            score += 22;
        }
        if (files.stream().anyMatch(file -> file.toLowerCase(Locale.US).contains("payment")
                || file.toLowerCase(Locale.US).contains("checkout")
                || file.toLowerCase(Locale.US).contains("auth"))) {
            score += 16;
        }
        if (title.contains("retry") || title.contains("ledger") || title.contains("authorization")) {
            score += 12;
        }
        if (linkedDeployment != null) {
            score += Math.min(18, linkedDeployment.getDependencies().size() * 4);
            score += linkedDeployment.getRiskAssessment() == null ? 0 : linkedDeployment.getRiskAssessment().score() / 5;
        }

        return Math.min(100, score);
    }

    private PullRequestRecommendation recommendationFor(int score, String ciStatus) {
        if (score >= 75) {
            return PullRequestRecommendation.BLOCK;
        }
        if (score >= 45 || !"success".equalsIgnoreCase(ciStatus)) {
            return PullRequestRecommendation.WAIT;
        }
        return PullRequestRecommendation.MERGE;
    }

}
