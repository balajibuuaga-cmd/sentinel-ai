package com.sentinelai.service.ai;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.PullRequestRecommendation;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.RiskLevel;
import com.sentinelai.model.RiskReason;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "sentinel.ai.provider", havingValue = "deterministic", matchIfMissing = true)
public class DeterministicChiefEngineerReasoningProvider implements ChiefEngineerReasoningProvider {

    @Override
    public String name() {
        return "deterministic-chief-engineer";
    }

    @Override
    public String deploymentRecommendation(int score) {
        if (score >= 85) {
            return "Block deployment until the failing signals are resolved and an incident commander approves.";
        }
        if (score >= 65) {
            return "Delay deployment and require senior engineer approval before release.";
        }
        if (score >= 35) {
            return "Proceed only after targeted regression checks pass.";
        }
        return "Safe to deploy with normal monitoring.";
    }

    @Override
    public String deploymentExplanation(Deployment deployment, int score, RiskLevel level, List<RiskReason> reasons) {
        String strongestReason = reasons.isEmpty()
                ? "no major correlated risk signals"
                : reasons.get(0).evidence();

        return "Sentinel AI rates " + deployment.getDeploymentKey() + " as " + level
                + " risk (" + score + "%) because " + strongestReason
                + " The score combines code, CI, incident history, runtime, and dependency signals.";
    }

    @Override
    public String pullRequestExplanation(
            PullRequestReviewRequest request,
            Deployment linkedDeployment,
            int score,
            PullRequestRecommendation recommendation
    ) {
        String linkedMemory = linkedDeployment == null
                ? "I did not find an active deployment review for this service."
                : "I linked this PR to " + linkedDeployment.getDeploymentKey() + " and "
                + linkedDeployment.getDependencies().size() + " dependency edges.";
        return "AI Engineer recommendation: " + recommendation + ". Risk is " + score + "% because PR #"
                + request.prNumber() + " changes " + request.changedFiles().size() + " files, CI is "
                + request.ciStatus() + ", and " + linkedMemory;
    }

    @Override
    public String executiveChiefBriefing(
            String organizationName,
            int deploymentCount,
            int dependencyCount,
            int auditEventCount,
            Deployment riskiest,
            RiskReason strongestReason
    ) {
        RiskAssessment assessment = riskiest.getRiskAssessment();
        return "Executive briefing: I reviewed " + deploymentCount + " deployments, " + dependencyCount
                + " dependency edges, and " + auditEventCount + " audit events for " + organizationName + ". "
                + riskiest.getServiceName() + " is the release I would not rubber-stamp: "
                + assessment.score() + "% " + assessment.level() + " risk because " + strongestReason.evidence()
                + " My recommendation is: " + assessment.recommendation();
    }

    @Override
    public String deploymentQuestionAnswer(DeploymentQuestionContext context) {
        String normalized = context.normalizedQuestion();
        Deployment deployment = context.deployment();
        RiskAssessment assessment = context.assessment();

        if (normalized.contains("briefing") || normalized.contains("morning") || normalized.contains("executive")) {
            return context.executiveBriefing();
        }

        if (normalized.contains("memory") || normalized.contains("history") || normalized.contains("similar")) {
            return context.memoryAnswer();
        }

        if (normalized.contains("dna") || normalized.contains("maturity") || normalized.contains("engineering")) {
            return context.engineeringDnaAnswer();
        }

        if (normalized.contains("unsafe") || normalized.contains("risk")) {
            return context.riskyReleaseCount() > 0
                    ? context.riskyReleaseCount() + " release" + (context.riskyReleaseCount() == 1 ? "" : "s")
                    + " need attention. The highest is " + deployment.getServiceName() + " at "
                    + assessment.score() + "% " + assessment.level() + " risk."
                    : "I do not see a high-risk release right now. I would still keep normal monitoring on staging changes.";
        }

        if (normalized.contains("block") || normalized.contains("why")) {
            String reasons = assessment.reasons().stream()
                    .limit(3)
                    .map(RiskReason::evidence)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("the release has correlated risk signals.");
            return "I would consider blocking " + deployment.getServiceName()
                    + " if the team cannot resolve these signals: " + reasons;
        }

        if (normalized.contains("next") || normalized.contains("do")) {
            return "Next action: ask " + deployment.getOwnerTeam()
                    + " to address the top evidence, rerun targeted CI, and only approve once the note explains how the blast radius was reduced.";
        }

        if (normalized.contains("approve")) {
            return deployment.getServiceName() + " is " + assessment.level()
                    + " risk. My approval advice is: " + assessment.recommendation();
        }

        String strongestSignal = assessment.reasons().isEmpty()
                ? "no dominant signal"
                : assessment.reasons().get(0).evidence();
        return "For " + deployment.getServiceName() + ", I see " + assessment.score()
                + "% " + assessment.level() + " risk. The strongest signal is: " + strongestSignal;
    }
}
