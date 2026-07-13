package com.sentinelai.model.intelligence;

import java.time.Instant;

public record ErrorEventView(
        String errorType,
        String message,
        String path,
        String httpMethod,
        String requestId,
        Instant occurredAt
) {
}
