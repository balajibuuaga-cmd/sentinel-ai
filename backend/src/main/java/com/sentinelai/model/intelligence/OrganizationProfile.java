package com.sentinelai.model.intelligence;

import java.util.List;

public record OrganizationProfile(
        String tenantId,
        String organizationName,
        String workspaceStatus,
        int deploymentCount,
        int prReviewCount,
        int architectureServiceCount,
        int auditEventCount,
        int connectedIntegrationCount,
        List<OnboardingStep> onboardingSteps
) {
}
