package com.sentinelai.model.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TeamInviteRequest(@NotBlank @Email String email, @NotBlank String role) {
}
