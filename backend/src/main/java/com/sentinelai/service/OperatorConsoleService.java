package com.sentinelai.service;

import com.sentinelai.RuntimeModeService;
import com.sentinelai.model.IntegrationStatus;
import com.sentinelai.model.IntegrationSyncEvent;
import com.sentinelai.model.IntegrationSyncStatus;
import com.sentinelai.model.intelligence.AiProviderStatus;
import com.sentinelai.model.operator.OperatorConsole;
import com.sentinelai.model.operator.OperatorFailure;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.IntegrationConnectionRepository;
import com.sentinelai.repository.IntegrationSyncEventRepository;
import com.sentinelai.security.AuthModeStatus;
import com.sentinelai.security.RateLimitService;
import com.sentinelai.security.TenantContext;
import com.sentinelai.security.TenantIdentity;
import com.sentinelai.security.TokenAuthenticationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OperatorConsoleService {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("requestId=([A-Za-z0-9._:-]+)");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("category=([A-Z_]+)");

    private final TokenAuthenticationService tokenAuthenticationService;
    private final AiProviderService aiProviderService;
    private final DeploymentRepository deploymentRepository;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final IntegrationSyncEventRepository integrationSyncEventRepository;
    private final MeterRegistry meterRegistry;
    private final TenantContext tenantContext;
    private final RateLimitService rateLimitService;
    private final BackgroundJobQueueService backgroundJobQueueService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final RuntimeModeService runtimeModeService;
    private final boolean realIntegrationExchangeEnabled;

    public OperatorConsoleService(
            TokenAuthenticationService tokenAuthenticationService,
            AiProviderService aiProviderService,
            DeploymentRepository deploymentRepository,
            IntegrationConnectionRepository integrationConnectionRepository,
            IntegrationSyncEventRepository integrationSyncEventRepository,
            MeterRegistry meterRegistry,
            TenantContext tenantContext,
            RateLimitService rateLimitService,
            BackgroundJobQueueService backgroundJobQueueService,
            WebhookDeliveryService webhookDeliveryService,
            RuntimeModeService runtimeModeService,
            @Value("${sentinel.integrations.real-exchange-enabled:false}") boolean realIntegrationExchangeEnabled
    ) {
        this.tokenAuthenticationService = tokenAuthenticationService;
        this.aiProviderService = aiProviderService;
        this.deploymentRepository = deploymentRepository;
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.integrationSyncEventRepository = integrationSyncEventRepository;
        this.meterRegistry = meterRegistry;
        this.tenantContext = tenantContext;
        this.rateLimitService = rateLimitService;
        this.backgroundJobQueueService = backgroundJobQueueService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.runtimeModeService = runtimeModeService;
        this.realIntegrationExchangeEnabled = realIntegrationExchangeEnabled;
    }

    public OperatorConsole current() {
        TenantIdentity tenant = tenantContext.current();
        AuthModeStatus auth = tokenAuthenticationService.status();
        AiProviderStatus ai = aiProviderService.status();
        long attentionIntegrations = integrationConnectionRepository.countByTenantIdAndStatus(
                tenant.tenantId(),
                IntegrationStatus.NEEDS_ATTENTION
        );

        return new OperatorConsole(
                RequestContext.requestId(),
                tenant.tenantId(),
                tenant.organizationName(),
                runtimeModeService.mode(),
                runtimeModeService.apiEnabled(),
                runtimeModeService.workerEnabled(),
                auth.mode(),
                auth.cognitoConfigured(),
                ai.activeProvider(),
                ai.configuredProvider(),
                ai.model(),
                ai.externalCallsEnabled(),
                realIntegrationExchangeEnabled,
                realIntegrationExchangeEnabled ? "live provider sync" : "simulated provider sync",
                rateLimitService.redisEnabled(),
                rateLimitService.backend(),
                rateLimitService.limit(),
                readinessStatus(auth, attentionIntegrations),
                deploymentRepository.findByTenantId(tenant.tenantId()).size(),
                integrationConnectionRepository.countByTenantIdAndStatus(tenant.tenantId(), IntegrationStatus.CONNECTED),
                attentionIntegrations,
                backgroundJobQueueService.summary(tenant.tenantId()),
                webhookDeliveryService.failedCount(tenant.tenantId()),
                metrics(),
                recentFailures(tenant.tenantId())
        );
    }

    private String readinessStatus(AuthModeStatus auth, long attentionIntegrations) {
        if ("cognito".equalsIgnoreCase(auth.mode()) && !auth.cognitoConfigured()) {
            return "auth configuration required";
        }
        if (attentionIntegrations > 0) {
            return "provider attention required";
        }
        return "ready";
    }

    private Map<String, Double> metrics() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("deploymentReviewsCreated", counter("sentinel_deployment_reviews_created_total"));
        values.put("approvalDecisions", counter("sentinel_approval_decisions_total"));
        values.put("webhooksIngested", counter("sentinel_webhooks_ingested_total"));
        values.put("providerSyncAttempts", counter("sentinel_provider_sync_attempts_total"));
        values.put("providerSyncFailures", counter("sentinel_provider_sync_failures_total"));
        return values;
    }

    private double counter(String name) {
        return meterRegistry.find(name)
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();
    }

    private List<OperatorFailure> recentFailures(String tenantId) {
        return integrationSyncEventRepository.findTop30ByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .filter(event -> event.getStatus() == IntegrationSyncStatus.FAILED
                        || event.getStatus() == IntegrationSyncStatus.DEGRADED)
                .limit(6)
                .map(this::toFailure)
                .toList();
    }

    private OperatorFailure toFailure(IntegrationSyncEvent event) {
        String detail = event.getDetail();
        return new OperatorFailure(
                event.getProvider().name(),
                extract(CATEGORY_PATTERN, detail, event.getStatus().name()),
                sanitizeDetail(detail),
                extract(REQUEST_ID_PATTERN, detail, "not captured"),
                event.getCreatedAt()
        );
    }

    private String extract(Pattern pattern, String detail, String fallback) {
        if (detail == null) {
            return fallback;
        }
        Matcher matcher = pattern.matcher(detail);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private String sanitizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Provider sync reported a degraded state.";
        }
        return detail.replaceAll("tokenSecretRef=[^\\s,]+", "tokenSecretRef=redacted");
    }
}
