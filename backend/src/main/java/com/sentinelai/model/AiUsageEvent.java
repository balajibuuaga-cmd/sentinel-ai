package com.sentinelai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per real Bedrock LLM call. Token counts and latency come straight from
 * the Bedrock Converse response; cost is estimated from published per-token pricing
 * (Bedrock does not return billed dollars). Failed rows are fallback triggers:
 * the deterministic provider answered instead.
 */
@Entity
@Table(name = "ai_usage_events")
public class AiUsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int inputTokens;

    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(nullable = false)
    private Instant createdAt;

    protected AiUsageEvent() {
    }

    public AiUsageEvent(
            String tenantId,
            String organizationName,
            String operation,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            BigDecimal estimatedCostUsd,
            boolean succeeded,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.operation = operation;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.estimatedCostUsd = estimatedCostUsd;
        this.succeeded = succeeded;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOperation() {
        return operation;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
