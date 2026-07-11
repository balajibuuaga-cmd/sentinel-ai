package com.sentinelai.model.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SecretScanRequest(
        @NotBlank @Size(max = 100_000) String content,
        @Size(max = 255) String filename
) {
}
