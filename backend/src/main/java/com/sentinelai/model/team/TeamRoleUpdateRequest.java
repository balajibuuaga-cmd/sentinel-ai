package com.sentinelai.model.team;

import jakarta.validation.constraints.NotBlank;

public record TeamRoleUpdateRequest(@NotBlank String role) {
}
