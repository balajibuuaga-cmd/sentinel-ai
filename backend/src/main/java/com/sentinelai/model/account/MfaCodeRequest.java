package com.sentinelai.model.account;

import jakarta.validation.constraints.NotBlank;

public record MfaCodeRequest(@NotBlank String code) {
}
