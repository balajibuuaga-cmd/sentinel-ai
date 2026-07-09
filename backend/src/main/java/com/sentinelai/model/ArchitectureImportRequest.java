package com.sentinelai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ArchitectureImportRequest(
        @NotNull List<@Valid ServiceInput> services,
        @NotNull List<@Valid DependencyInput> dependencies
) {
    public record ServiceInput(
            String serviceName,
            String ownerTeam,
            String runtime,
            String tier,
            String repository,
            String description
    ) {
    }

    public record DependencyInput(
            String sourceService,
            String targetService,
            ArchitectureDependencyType dependencyType,
            String criticality,
            String notes
    ) {
    }
}
