package com.sentinelai.service;

import com.sentinelai.model.AiUsageEvent;
import com.sentinelai.model.intelligence.AiUsageEventView;
import com.sentinelai.model.intelligence.AiUsageOperationStats;
import com.sentinelai.model.intelligence.AiUsageSummary;
import com.sentinelai.repository.AiUsageEventRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.security.TenantIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    /**
     * Published on-demand USD prices per million tokens (input, output) for Claude
     * models on Bedrock, matched by model-id substring. Bedrock does not return
     * billed dollars per call, so cost is always an estimate derived from the real
     * token counts it does return.
     */
    private static final Map<String, BigDecimal[]> PRICES_PER_MILLION_TOKENS = Map.of(
            "claude-opus-4", new BigDecimal[]{new BigDecimal("5.00"), new BigDecimal("25.00")},
            "claude-sonnet", new BigDecimal[]{new BigDecimal("3.00"), new BigDecimal("15.00")},
            "claude-haiku", new BigDecimal[]{new BigDecimal("1.00"), new BigDecimal("5.00")}
    );

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final AiUsageEventRepository aiUsageEventRepository;
    private final TenantContext tenantContext;

    public AiUsageService(AiUsageEventRepository aiUsageEventRepository, TenantContext tenantContext) {
        this.aiUsageEventRepository = aiUsageEventRepository;
        this.tenantContext = tenantContext;
    }

    /**
     * Records one Bedrock call. Never throws: usage accounting must not break the
     * AI feature it is observing. Runs in its own transaction because callers
     * (deployment analysis, briefings, copilot) execute inside readOnly
     * transactions where this INSERT would abort the caller's transaction on
     * PostgreSQL.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String operation, String model, int inputTokens, int outputTokens, long latencyMs, boolean succeeded) {
        try {
            TenantIdentity tenant = tenantContext.current();
            aiUsageEventRepository.save(new AiUsageEvent(
                    tenant.tenantId(),
                    tenant.organizationName(),
                    operation,
                    model,
                    inputTokens,
                    outputTokens,
                    latencyMs,
                    estimateCostUsd(model, inputTokens, outputTokens),
                    succeeded,
                    Instant.now()
            ));
        } catch (Exception ex) {
            log.warn("Failed to record AI usage event for operation {}", operation, ex);
        }
    }

    public AiUsageSummary summary() {
        String tenantId = tenantContext.tenantId();
        List<AiUsageEvent> events = aiUsageEventRepository.findByTenantId(tenantId);

        long successfulCalls = events.stream().filter(AiUsageEvent::isSucceeded).count();
        long totalInputTokens = events.stream().mapToLong(AiUsageEvent::getInputTokens).sum();
        long totalOutputTokens = events.stream().mapToLong(AiUsageEvent::getOutputTokens).sum();
        BigDecimal totalCost = events.stream()
                .map(AiUsageEvent::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<AiUsageEvent>> byOperation = new LinkedHashMap<>();
        events.stream()
                .sorted(Comparator.comparing(AiUsageEvent::getOperation))
                .forEach(event -> byOperation.computeIfAbsent(event.getOperation(), key -> new java.util.ArrayList<>()).add(event));

        List<AiUsageOperationStats> operationStats = byOperation.entrySet().stream()
                .map(entry -> {
                    List<AiUsageEvent> operationEvents = entry.getValue();
                    return new AiUsageOperationStats(
                            entry.getKey(),
                            operationEvents.size(),
                            operationEvents.stream().filter(event -> !event.isSucceeded()).count(),
                            operationEvents.stream().mapToLong(AiUsageEvent::getInputTokens).sum(),
                            operationEvents.stream().mapToLong(AiUsageEvent::getOutputTokens).sum(),
                            operationEvents.stream().map(AiUsageEvent::getEstimatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add),
                            averageLatency(operationEvents)
                    );
                })
                .sorted(Comparator.comparingLong(AiUsageOperationStats::calls).reversed())
                .toList();

        List<AiUsageEventView> recentCalls = aiUsageEventRepository.findTop25ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(event -> new AiUsageEventView(
                        event.getOperation(),
                        event.getModel(),
                        event.getInputTokens(),
                        event.getOutputTokens(),
                        event.getLatencyMs(),
                        event.getEstimatedCostUsd(),
                        event.isSucceeded(),
                        event.getCreatedAt()
                ))
                .toList();

        return new AiUsageSummary(
                events.size(),
                successfulCalls,
                events.size() - successfulCalls,
                totalInputTokens,
                totalOutputTokens,
                totalCost,
                averageLatency(events),
                operationStats,
                recentCalls
        );
    }

    public static BigDecimal estimateCostUsd(String model, int inputTokens, int outputTokens) {
        BigDecimal[] prices = PRICES_PER_MILLION_TOKENS.entrySet().stream()
                .filter(entry -> model != null && model.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (prices == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal inputCost = prices[0].multiply(BigDecimal.valueOf(inputTokens)).divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = prices[1].multiply(BigDecimal.valueOf(outputTokens)).divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    private long averageLatency(List<AiUsageEvent> events) {
        List<AiUsageEvent> measured = events.stream().filter(event -> event.getLatencyMs() > 0).toList();
        if (measured.isEmpty()) {
            return 0;
        }
        return Math.round(measured.stream().mapToLong(AiUsageEvent::getLatencyMs).average().orElse(0));
    }
}
