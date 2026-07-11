package com.sentinelai.service.ai;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.PullRequestRecommendation;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.model.RiskLevel;
import com.sentinelai.model.RiskReason;

import java.util.List;
import java.util.Optional;

public interface ChiefEngineerReasoningProvider {

    String name();

    String deploymentRecommendation(int score);

    String deploymentExplanation(Deployment deployment, int score, RiskLevel level, List<RiskReason> reasons);

    String pullRequestExplanation(
            PullRequestReviewRequest request,
            Deployment linkedDeployment,
            int score,
            PullRequestRecommendation recommendation
    );

    String executiveChiefBriefing(
            String organizationName,
            int deploymentCount,
            int dependencyCount,
            int auditEventCount,
            Deployment riskiest,
            RiskReason strongestReason
    );

    String deploymentQuestionAnswer(DeploymentQuestionContext context);

    /**
     * Downgrade gate: the deterministic scanner fired and every candidate value
     * in the window is already masked. Decide from context alone whether the hit
     * is a false alarm. Empty means no AI judgment is available - callers must
     * fall back to keeping the hit blocked.
     */
    default Optional<SecretGateVerdict> judgeMaskedScannerHit(String extension, List<String> maskedLines, int focusLine) {
        return Optional.empty();
    }

    /**
     * Risk gate: the scanner did not fire but the focus line looks
     * secret-bearing; the window is raw. Empty means no AI judgment is
     * available - callers fall back to a deterministic heuristic.
     */
    default Optional<SecretGateVerdict> judgeRiskCandidate(String extension, List<String> rawLines, int focusLine) {
        return Optional.empty();
    }
}
