package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RiskReason {

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 1000)
    private String evidence;

    private int impact;

    protected RiskReason() {
    }

    public RiskReason(String category, String evidence, int impact) {
        this.category = category;
        this.evidence = evidence;
        this.impact = impact;
    }

    public String category() {
        return category;
    }

    public String evidence() {
        return evidence;
    }

    public int impact() {
        return impact;
    }

    public String getCategory() {
        return category;
    }

    public String getEvidence() {
        return evidence;
    }

    public int getImpact() {
        return impact;
    }
}
