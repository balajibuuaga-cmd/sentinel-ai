package com.sentinelai.observability;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String requestId,
        String code,
        String message,
        Map<String, String> details,
        Instant timestamp
) {
}
