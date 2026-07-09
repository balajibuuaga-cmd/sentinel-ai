package com.sentinelai.service;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.EngineeringEvent;
import com.sentinelai.model.MemoryLink;
import com.sentinelai.model.PullRequestReview;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.RiskReason;
import com.sentinelai.model.intelligence.CommandResponse;
import com.sentinelai.model.intelligence.DeploymentMemory;
import com.sentinelai.model.intelligence.DnaScore;
import com.sentinelai.model.intelligence.EngineeringDna;
import com.sentinelai.model.intelligence.ExecutiveBriefing;
import com.sentinelai.model.intelligence.MemoryEvent;
import com.sentinelai.model.intelligence.MetricInsight;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.PullRequestReviewRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.service.ai.ChiefEngineerReasoningProvider;
import com.sentinelai.service.ai.DeploymentQuestionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class IntelligenceService {

    private final DeploymentRepository deploymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final EngineeringMemoryService engineeringMemoryService;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final ArchitectureBrainService architectureBrainService;
    private final EngineeringPlaybookService engineeringPlaybookService;
    private final TenantContext tenantContext;
    private final ChiefEngineerReasoningProvider reasoningProvider;

    public IntelligenceService(
            DeploymentRepository deploymentRepository,
            AuditEventRepository auditEventRepository,
            EngineeringMemoryService engineeringMemoryService,
            PullRequestReviewRepository pullRequestReviewRepository,
            ArchitectureBrainService architectureBrainService,
            EngineeringPlaybookService engineeringPlaybookService,
            TenantContext tenantContext,
            ChiefEngineerReasoningProvider reasoningProvider
    ) {
        this.deploymentRepository = deploymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.engineeringMemoryService = engineeringMemoryService;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.architectureBrainService = architectureBrainService;
        this.engineeringPlaybookService = engineeringPlaybookService;
        this.tenantContext = tenantContext;
        this.reasoningProvider = reasoningProvider;
    }

    @Transactional(readOnly = true)
    public ExecutiveBriefing executiveBriefing() {
        List<Deployment> deployments = deploymentsByRisk();
        List<AuditEvent> auditEvents = auditEventRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
        OrgIntelligence intel = orgIntelligence(deployments);
        Deployment riskiest = deployments.isEmpty() ? null : deployments.get(0);

        if (riskiest == null) {
            return new ExecutiveBriefing(
                    greeting(),
                    "I do not see active deployment reviews yet. Ingest a GitHub signal and I will produce a briefing.",
                    "Connect release signals.",
                    "I need GitHub, CI, and dependency evidence before I can make a release judgment.",
                    "I do not see active deployment reviews. Connect GitHub or simulate a PR signal and I will start reasoning over it.",
                    List.of(
                            new MetricInsight("Deployments", "0"),
                            new MetricInsight("Production", "0"),
                            new MetricInsight("High risk", "0"),
                            new MetricInsight("Expected savings", "$0")
                    )
            );
        }

        RiskAssessment assessment = riskiest.getRiskAssessment();
        String strongestReason = assessment.reasons().isEmpty()
                ? "the release has correlated risk signals"
                : assessment.reasons().get(0).evidence();
        RiskReason chiefReason = assessment.reasons().isEmpty()
                ? new RiskReason("Release evidence", strongestReason, 0)
                : assessment.reasons().get(0);

        return new ExecutiveBriefing(
                greeting(),
                "For " + tenantContext.organizationName() + ", I reviewed " + intel.total + " deployments, " + auditEvents.size() + " decisions, "
                        + intel.dependencyCount + " service dependencies, and the current security posture. "
                        + riskiest.getOwnerTeam() + " is carrying the highest release risk.",
                riskiest.getServiceName() + " should not be treated as routine.",
                assessment.recommendation(),
                reasoningProvider.executiveChiefBriefing(
                        tenantContext.organizationName(),
                        intel.total,
                        intel.dependencyCount,
                        auditEvents.size(),
                        riskiest,
                        chiefReason
                ),
                List.of(
                        new MetricInsight("Deployments", String.valueOf(intel.total)),
                        new MetricInsight("Production", String.valueOf(intel.production)),
                        new MetricInsight("High risk", String.valueOf(intel.highRisk)),
                        new MetricInsight("Expected savings", "$" + String.format(Locale.US, "%,d", intel.projectedSavings))
                )
        );
    }

    @Transactional(readOnly = true)
    public EngineeringDna engineeringDna() {
        OrgIntelligence intel = orgIntelligence(deploymentRepository.findByTenantId(tenantContext.tenantId()));
        int velocity = clamp(72 + intel.total * 5 - intel.blocked * 4, 54, 98);
        int resilience = clamp(91 - intel.highRisk * 12 + intel.approved * 3, 35, 95);
        int ownership = clamp(82 - intel.dependencyCount + intel.production * 2, 48, 94);
        int reviewDiscipline = clamp(88 - intel.avgRisk / 2, 42, 96);

        return new EngineeringDna(
                intel.maturity,
                "Engineering DNA is " + intel.maturity + "/100. Sentinel sees healthy delivery motion, but release discipline should tighten around high-risk production changes.",
                List.of(
                        new DnaScore("Maturity", intel.maturity),
                        new DnaScore("Velocity", velocity),
                        new DnaScore("Resilience", resilience),
                        new DnaScore("Ownership", ownership),
                        new DnaScore("Review discipline", reviewDiscipline)
                )
        );
    }

    @Transactional
    public DeploymentMemory deploymentMemory(long deploymentId) {
        Deployment deployment = deploymentRepository.findByIdAndTenantId(deploymentId, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
        List<MemoryLink> links = engineeringMemoryService.memoryLinksFor(deployment.getId());
        if (links.isEmpty()) {
            engineeringMemoryService.recordDeploymentCreated(deployment, "MEMORY_BACKFILL");
            links = engineeringMemoryService.memoryLinksFor(deployment.getId());
        }

        List<MemoryEvent> events = links.stream()
                .map(this::toMemoryEvent)
                .toList();
        int confidence = links.stream()
                .mapToInt(MemoryLink::getConfidence)
                .max()
                .orElseGet(() -> Math.min(96, 72 + deployment.getRiskAssessment().reasons().size() * 4));

        return new DeploymentMemory(
                deployment.getId(),
                deployment.getServiceName(),
                confidence,
                "My memory links " + deployment.getServiceName() + " to " + events.size()
                        + " operational patterns. Confidence: " + confidence + "%.",
                events
        );
    }

    @Transactional(readOnly = true)
    public CommandResponse answerCommand(String command, Long deploymentId) {
        String normalized = command.toLowerCase(Locale.US);
        Deployment deployment = deploymentId == null
                ? deploymentsByRisk().stream().findFirst().orElse(null)
                : deploymentRepository.findByIdAndTenantId(deploymentId, tenantContext.tenantId()).orElse(null);

        if (normalized.contains("merge") || normalized.contains("pr #") || normalized.contains("pull request")) {
            return latestPullRequestAnswer()
                    .map(CommandResponse::new)
                    .orElseGet(() -> new CommandResponse("I have not reviewed a pull request yet. Simulate one in AI Engineer mode and I will give a merge recommendation."));
        }

        if (normalized.contains("architecture") || normalized.contains("fragile")
                || normalized.contains("blast radius") || normalized.contains("depends on")
                || normalized.contains("refactor")) {
            return new CommandResponse(architectureBrainService.answerArchitectureQuestion(normalized));
        }

        if (isPlaybookQuestion(normalized, deployment)) {
            return new CommandResponse(engineeringPlaybookService.answer(command, deployment));
        }

        if (deployment == null) {
            return new CommandResponse("I need a deployment signal first. Simulate a GitHub PR and I will review the release.");
        }

        RiskAssessment assessment = deployment.getRiskAssessment();
        List<Deployment> deployments = deploymentRepository.findByTenantId(tenantContext.tenantId());
        long riskyCount = deployments.stream()
                .filter(item -> List.of("HIGH", "CRITICAL").contains(item.getRiskAssessment().level().name()))
                .count();

        DeploymentMemory memory = deploymentMemory(deployment.getId());
        String memoryAnswer = memory.summary() + " " + memory.events().stream()
                .map(event -> event.date() + " " + event.title())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");

        return new CommandResponse(reasoningProvider.deploymentQuestionAnswer(new DeploymentQuestionContext(
                normalized,
                deployment,
                assessment,
                riskyCount,
                executiveBriefing().chiefBriefing(),
                memoryAnswer,
                engineeringDna().summary()
        )));
    }

    private boolean isPlaybookQuestion(String normalized, Deployment deployment) {
        // Strip the deployment's own service name first so keywords like "api" or "auth" that
        // happen to be a substring of a hyphenated service name (e.g. "payment-api") don't
        // misroute a genuine deployment question into the generic playbook path.
        String withoutServiceName = deployment == null
                ? normalized
                : normalized.replace(deployment.getServiceName().toLowerCase(Locale.US), "");
        return withoutServiceName.contains("playbook") || withoutServiceName.contains("checklist")
                || withoutServiceName.contains("backend") || withoutServiceName.contains("api")
                || withoutServiceName.contains("auth") || withoutServiceName.contains("security")
                || withoutServiceName.contains("cache") || withoutServiceName.contains("scale")
                || withoutServiceName.contains("database") || withoutServiceName.contains("cloud")
                || withoutServiceName.contains("observability") || withoutServiceName.contains("incident");
    }

    private java.util.Optional<String> latestPullRequestAnswer() {
        return pullRequestReviewRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId())
                .map(this::pullRequestAnswer);
    }

    private String pullRequestAnswer(PullRequestReview review) {
        String decision = review.getDecision() == null
                ? "No human decision has been recorded yet."
                : "Human decision recorded: " + review.getDecision() + ".";
        return "For " + review.getRepository() + " PR #" + review.getPrNumber()
                + ", my recommendation is " + review.getRecommendation() + ". Risk is "
                + review.getRiskScore() + "%. " + review.getExplanation() + " " + decision;
    }

    private List<Deployment> deploymentsByRisk() {
        return deploymentRepository.findByTenantId(tenantContext.tenantId()).stream()
                .sorted(Comparator.comparingInt(this::riskScore).reversed())
                .toList();
    }

    private MemoryEvent toMemoryEvent(MemoryLink link) {
        EngineeringEvent event = link.getEngineeringEvent();
        String date = "Now";
        if (event.getOccurredAt() != null) {
            date = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)
                    .withZone(java.time.ZoneOffset.UTC)
                    .format(event.getOccurredAt());
        }
        return new MemoryEvent(
                date,
                event.getTitle(),
                event.getDetails() + " Memory link: " + link.getReason()
        );
    }

    private OrgIntelligence orgIntelligence(List<Deployment> deployments) {
        int total = deployments.size();
        int highRisk = (int) deployments.stream()
                .filter(item -> List.of("HIGH", "CRITICAL").contains(item.getRiskAssessment().level().name()))
                .count();
        int blocked = (int) deployments.stream()
                .filter(item -> "BLOCKED".equals(item.getStatus().name()))
                .count();
        int approved = (int) deployments.stream()
                .filter(item -> "APPROVED".equals(item.getStatus().name()))
                .count();
        int production = (int) deployments.stream()
                .filter(item -> "production".equalsIgnoreCase(item.getEnvironment()))
                .count();
        int avgRisk = total == 0
                ? 0
                : Math.round((float) deployments.stream().mapToInt(this::riskScore).sum() / total);
        int dependencyCount = deployments.stream()
                .mapToInt(item -> item.getDependencies().size())
                .sum();
        int maturity = clamp(92 - avgRisk + approved * 3 - blocked * 5, 42, 96);
        int projectedSavings = Math.max(18000, Math.round((highRisk * 26000 + blocked * 14000 + dependencyCount * 950) / 1000.0f) * 1000);

        return new OrgIntelligence(total, highRisk, blocked, approved, production, avgRisk, dependencyCount, maturity, projectedSavings);
    }

    private int riskScore(Deployment deployment) {
        return deployment.getRiskAssessment() == null ? 0 : deployment.getRiskAssessment().score();
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "Good morning.";
        }
        if (hour < 17) {
            return "Good afternoon.";
        }
        return "Good evening.";
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record OrgIntelligence(
            int total,
            int highRisk,
            int blocked,
            int approved,
            int production,
            int avgRisk,
            int dependencyCount,
            int maturity,
            int projectedSavings
    ) {
    }
}
