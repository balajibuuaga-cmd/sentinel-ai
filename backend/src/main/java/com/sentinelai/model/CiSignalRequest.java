package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CiSignalRequest(
        @NotBlank String provider,
        @NotBlank String repository,
        @NotBlank String serviceName,
        @NotBlank String ownerTeam,
        @NotBlank String environment,
        @NotBlank String commitSha,
        @NotBlank String pipelineName,
        String buildUrl,
        @NotBlank String status,
        Integer failedTests,
        Integer coverageDelta,
        String actor,
        List<String> failedSuites,
        List<String> dependencies
) {
}
