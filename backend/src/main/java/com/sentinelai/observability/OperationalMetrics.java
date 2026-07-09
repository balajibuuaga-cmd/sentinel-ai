package com.sentinelai.observability;

import com.sentinelai.model.ApprovalDecision;
import com.sentinelai.model.IntegrationProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class OperationalMetrics {

    private final MeterRegistry meterRegistry;

    public OperationalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void deploymentReviewCreated(String source) {
        meterRegistry.counter("sentinel_deployment_reviews_created_total", "source", source).increment();
    }

    public void approvalDecision(ApprovalDecision decision) {
        meterRegistry.counter("sentinel_approval_decisions_total", "decision", decision.name()).increment();
    }

    public void webhookIngested(String provider) {
        meterRegistry.counter("sentinel_webhooks_ingested_total", "provider", provider).increment();
    }

    public void providerSyncAttempt(IntegrationProvider provider, String mode) {
        meterRegistry.counter("sentinel_provider_sync_attempts_total", "provider", provider.name(), "mode", mode).increment();
    }

    public void providerSyncFailure(IntegrationProvider provider, String category) {
        meterRegistry.counter("sentinel_provider_sync_failures_total", "provider", provider.name(), "category", category).increment();
    }
}
