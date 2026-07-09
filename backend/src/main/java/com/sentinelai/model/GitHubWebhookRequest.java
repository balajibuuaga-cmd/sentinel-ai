package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record GitHubWebhookRequest(
        @NotBlank String repository,
        @NotBlank String serviceName,
        @NotBlank String ownerTeam,
        @NotBlank String environment,
        @NotBlank String commitSha,
        @NotBlank String pullRequestTitle,
        String actor,
        String ciStatus,
        List<String> changedFiles,
        List<String> dependencies
) {
}
