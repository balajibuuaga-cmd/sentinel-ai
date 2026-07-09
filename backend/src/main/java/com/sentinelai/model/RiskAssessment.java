package com.sentinelai.model;

import java.time.Instant;
import java.util.List;

public record RiskAssessment(
        int score,
        RiskLevel level,
        String recommendation,
        String aiExplanation,
        List<RiskReason> reasons,
        Instant assessedAt
) {
}
