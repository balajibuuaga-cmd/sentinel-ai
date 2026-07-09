package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
public class IncidentTimelineEvent {

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 1000)
    private String detail;

    protected IncidentTimelineEvent() {
    }

    public IncidentTimelineEvent(Instant occurredAt, String actor, String label, String detail) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.label = label;
        this.detail = detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }
}
