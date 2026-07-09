package com.sentinelai.security;

import jakarta.validation.constraints.NotBlank;

public record CognitoExchangeRequest(
        @NotBlank String code,
        @NotBlank String redirectUri
) {
}
