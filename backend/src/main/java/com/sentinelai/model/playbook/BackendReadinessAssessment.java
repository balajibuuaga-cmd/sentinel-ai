package com.sentinelai.model.playbook;

import java.util.List;

public record BackendReadinessAssessment(
        int overallScore,
        String maturityLevel,
        String summary,
        List<BackendReadinessCheck> checks,
        List<String> nextActions
) {
}
