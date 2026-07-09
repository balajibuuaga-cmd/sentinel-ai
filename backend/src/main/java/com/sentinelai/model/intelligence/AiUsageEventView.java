package com.sentinelai.model.intelligence;

import java.math.BigDecimal;
import java.time.Instant;

public record AiUsageEventView(
        String operation,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        BigDecimal estimatedCostUsd,
        boolean succeeded,
        Instant createdAt
) {
}
