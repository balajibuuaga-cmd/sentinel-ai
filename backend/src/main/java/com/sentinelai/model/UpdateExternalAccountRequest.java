package com.sentinelai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Retarget a connected integration at a different account or repository. Typed
 * and validated so the endpoint rejects blank or oversized input at the edge with
 * a clear 400, rather than relying on service-layer checks and the column bound.
 * The 255 cap matches the {@code external_account} column.
 */
public record UpdateExternalAccountRequest(
        @NotBlank @Size(max = 255) String externalAccount
) {
}
