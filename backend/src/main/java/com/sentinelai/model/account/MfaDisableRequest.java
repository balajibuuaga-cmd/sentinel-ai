package com.sentinelai.model.account;

import jakarta.validation.constraints.NotBlank;

public record MfaDisableRequest(@NotBlank String password) {
}
