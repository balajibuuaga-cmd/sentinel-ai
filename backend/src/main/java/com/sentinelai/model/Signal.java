package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class Signal {

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 800)
    private String description;

    private int riskWeight;

    protected Signal() {
    }

    public Signal(SignalType type, String title, String description, int riskWeight) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.riskWeight = riskWeight;
    }

    public SignalType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public int riskWeight() {
        return riskWeight;
    }

    public SignalType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getRiskWeight() {
        return riskWeight;
    }
}
