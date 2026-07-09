package com.sentinelai.model.intelligence;

import java.math.BigDecimal;

public record AiUsageOperationStats(
        String operation,
        long calls,
        long failedCalls,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedCostUsd,
        long averageLatencyMs
) {
}
