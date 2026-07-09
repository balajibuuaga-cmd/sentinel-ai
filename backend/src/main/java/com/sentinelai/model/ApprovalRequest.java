package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalRequest(
        @NotNull ApprovalDecision decision,
        @NotBlank String approver,
        String note
) {
}
