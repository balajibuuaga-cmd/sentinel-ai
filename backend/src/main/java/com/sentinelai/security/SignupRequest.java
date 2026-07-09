package com.sentinelai.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank String organizationName,
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
