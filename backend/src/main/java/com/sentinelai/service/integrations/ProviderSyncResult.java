package com.sentinelai.service.integrations;

public record ProviderSyncResult(
        int recordsInspected,
        int latencyMs,
        int healthScore,
        String detail
) {
}
