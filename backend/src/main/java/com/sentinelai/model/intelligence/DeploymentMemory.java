package com.sentinelai.model.intelligence;

import java.util.List;

public record DeploymentMemory(
        long deploymentId,
        String serviceName,
        int confidence,
        String summary,
        List<MemoryEvent> events
) {
}
