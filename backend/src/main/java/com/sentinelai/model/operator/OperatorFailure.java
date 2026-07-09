package com.sentinelai.model.operator;

import java.time.Instant;

public record OperatorFailure(
        String provider,
        String category,
        String message,
        String requestId,
        Instant createdAt
) {
}
