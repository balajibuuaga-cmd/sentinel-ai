package com.sentinelai.service.ai;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.RiskAssessment;

public record DeploymentQuestionContext(
        String normalizedQuestion,
        Deployment deployment,
        RiskAssessment assessment,
        long riskyReleaseCount,
        String executiveBriefing,
        String memoryAnswer,
        String engineeringDnaAnswer
) {
}
