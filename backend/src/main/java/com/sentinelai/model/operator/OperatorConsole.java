package com.sentinelai.model.operator;

import java.util.List;
import java.util.Map;

public record OperatorConsole(
        String requestId,
        String tenantId,
        String organizationName,
        String runtimeMode,
        boolean apiEnabled,
        boolean workerEnabled,
        String authMode,
        boolean cognitoConfigured,
        String aiProvider,
        String configuredAiProvider,
        String aiModel,
        boolean externalAiCallsEnabled,
        boolean realIntegrationExchangeEnabled,
        String integrationMode,
        boolean redisRateLimitingEnabled,
        String rateLimitBackend,
        int rateLimitPerMinute,
        String readinessStatus,
        long deploymentCount,
        long connectedIntegrationCount,
        long attentionIntegrationCount,
        BackgroundJobSummary backgroundJobs,
        long failedWebhookDeliveryCount,
        Map<String, Double> metrics,
        List<OperatorFailure> recentFailures
) {
}
