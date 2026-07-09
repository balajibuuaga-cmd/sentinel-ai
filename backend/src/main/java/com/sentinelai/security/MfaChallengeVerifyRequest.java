package com.sentinelai.security;

import jakarta.validation.constraints.NotBlank;

public record MfaChallengeVerifyRequest(@NotBlank String challengeToken, @NotBlank String code) {
}
