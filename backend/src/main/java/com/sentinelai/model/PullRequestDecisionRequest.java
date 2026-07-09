package com.sentinelai.model;

import jakarta.validation.constraints.NotNull;

public record PullRequestDecisionRequest(
        @NotNull PullRequestDecision decision,
        String actor,
        String note
) {
}
