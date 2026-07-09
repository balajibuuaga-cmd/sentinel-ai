package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class PersistedRiskAssessment {

    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel level;

    @Column(length = 1000)
    private String recommendation;

    @Column(length = 1600)
    private String aiExplanation;

    private Instant assessedAt;

    protected PersistedRiskAssessment() {
    }

    public PersistedRiskAssessment(
            int score,
            RiskLevel level,
            String recommendation,
            String aiExplanation,
            Instant assessedAt
    ) {
        this.score = score;
        this.level = level;
        this.recommendation = recommendation;
        this.aiExplanation = aiExplanation;
        this.assessedAt = assessedAt;
    }

    public int getScore() {
        return score;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
