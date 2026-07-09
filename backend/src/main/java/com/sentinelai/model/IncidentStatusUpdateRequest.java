package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IncidentStatusUpdateRequest(
        @NotNull IncidentStatus status,
        @NotBlank String actor,
        String note
) {
}
