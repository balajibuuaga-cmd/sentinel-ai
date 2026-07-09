package com.sentinelai.security;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank String newPassword) {
}
