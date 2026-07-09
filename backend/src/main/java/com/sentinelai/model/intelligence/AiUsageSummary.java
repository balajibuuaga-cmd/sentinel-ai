package com.sentinelai.model.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record AiUsageSummary(
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        long totalInputTokens,
        long totalOutputTokens,
        BigDecimal estimatedCostUsd,
        long averageLatencyMs,
        List<AiUsageOperationStats> byOperation,
        List<AiUsageEventView> recentCalls
) {
}
