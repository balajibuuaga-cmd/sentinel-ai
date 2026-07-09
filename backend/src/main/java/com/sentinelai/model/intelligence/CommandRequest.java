package com.sentinelai.model.intelligence;

import jakarta.validation.constraints.NotBlank;

public record CommandRequest(
        @NotBlank String command,
        Long deploymentId
) {
}
