package com.sentinelai.model;

import java.util.List;

public record ArchitectureBrain(
        String summary,
        String recommendedRefactor,
        int serviceCount,
        int dependencyCount,
        int riskCount,
        List<ArchitectureServiceNode> services,
        List<ArchitectureDependency> dependencies,
        List<ArchitectureRisk> risks
) {
}
