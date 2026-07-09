package com.sentinelai.model.playbook;

public record BackendReadinessCheck(
        String category,
        String status,
        int score,
        String evidence,
        String gap,
        String nextAction
) {
}
