package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record JiraSignalRequest(
        @NotBlank String issueKey,
        @NotBlank String summary,
        @NotBlank String priority,
        @NotBlank String status,
        @NotBlank String issueType,
        @NotBlank String serviceName,
        @NotBlank String ownerTeam,
        @NotBlank String environment,
        String commitSha,
        String actor,
        List<String> labels,
        List<String> dependencies
) {
}
