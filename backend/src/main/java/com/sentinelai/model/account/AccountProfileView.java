package com.sentinelai.model.account;

import java.time.Instant;

public record AccountProfileView(
        String email,
        String role,
        String tenantId,
        String organizationName,
        Instant createdAt,
        Instant lastLoginAt,
        boolean mfaEnabled
) {
}
