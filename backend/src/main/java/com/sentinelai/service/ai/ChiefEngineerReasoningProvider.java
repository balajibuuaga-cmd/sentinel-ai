package com.sentinelai.service.ai;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.PullRequestRecommendation;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.model.RiskLevel;
import com.sentinelai.model.RiskReason;

import java.util.List;

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
}
