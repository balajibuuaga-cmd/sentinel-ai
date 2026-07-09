package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PullRequestReviewRequest(
        @NotBlank String repository,
        int prNumber,
        @NotBlank String title,
        @NotBlank String author,
        @NotBlank String serviceName,
        @NotBlank String ownerTeam,
        @NotBlank String ciStatus,
        @NotNull List<String> changedFiles
) {
}
