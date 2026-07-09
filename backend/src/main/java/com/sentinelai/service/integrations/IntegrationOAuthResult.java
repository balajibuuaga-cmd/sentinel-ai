package com.sentinelai.service.integrations;

public record IntegrationOAuthResult(
        String externalAccount,
        String accessToken,
        String refreshToken,
        int recordsInspected,
        int latencyMs,
        String detail
) {
}
