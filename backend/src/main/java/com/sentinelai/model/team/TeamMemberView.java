package com.sentinelai.model.team;

import java.time.Instant;

public record TeamMemberView(
        Long id,
        String email,
        String role,
        boolean locked,
        boolean you,
        Instant createdAt,
        Instant lastLoginAt
) {
}
