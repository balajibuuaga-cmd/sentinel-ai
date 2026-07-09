package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IncidentRemediationStepRequest(
        @NotNull IncidentRemediationStep step,
        @NotBlank String actor
) {
}
