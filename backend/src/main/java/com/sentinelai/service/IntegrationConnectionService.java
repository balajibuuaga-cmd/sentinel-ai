package com.sentinelai.service;

import com.sentinelai.RuntimeModeService;
import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.IntegrationInstallRequest;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.IntegrationSyncEvent;
import com.sentinelai.model.IntegrationSyncStatus;
import com.sentinelai.observability.OperationalEventLogger;
import com.sentinelai.observability.OperationalMetrics;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.IntegrationConnectionRepository;
import com.sentinelai.repository.IntegrationSyncEventRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.service.integrations.IntegrationOAuthResult;
import com.sentinelai.service.integrations.IntegrationTokenVault;
import com.sentinelai.service.integrations.ProviderSignalSyncService;
import com.sentinelai.service.integrations.ProviderSyncException;
import com.sentinelai.service.integrations.ProviderSyncResult;
import com.sentinelai.service.integrations.ProviderOAuthClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IntegrationConnectionService {

    private final IntegrationConnectionRepository repository;
    private final IntegrationSyncEventRepository syncEventRepository;
    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;
    private final ProviderOAuthClient providerOAuthClient;
    private final IntegrationTokenVault tokenVault;
    private final ProviderSignalSyncService providerSignalSyncService;
    private final OperationalMetrics operationalMetrics;
    private final OperationalEventLogger operationalEventLogger;
    private final BackgroundJobQueueService backgroundJobQueueService;
    private final RuntimeModeService runtimeModeService;

    public IntegrationConnectionService(
            IntegrationConnectionRepository repository,
            IntegrationSyncEventRepository syncEventRepository,
            AuditEventRepository auditEventRepository,
            TenantContext tenantContext,
            ProviderOAuthClient providerOAuthClient,
            IntegrationTokenVault tokenVault,
            ProviderSignalSyncService providerSignalSyncService,
            OperationalMetrics operationalMetrics,
            OperationalEventLogger operationalEventLogger,
            BackgroundJobQueueService backgroundJobQueueService,
            RuntimeModeService runtimeModeService
    ) {
        this.repository = repository;
        this.syncEventRepository = syncEventRepository;
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
        this.providerOAuthClient = providerOAuthClient;
        this.tokenVault = tokenVault;
        this.providerSignalSyncService = providerSignalSyncService;
        this.operationalMetrics = operationalMetrics;
        this.operationalEventLogger = operationalEventLogger;
        this.backgroundJobQueueService = backgroundJobQueueService;
        this.runtimeModeService = runtimeModeService;
    }

    @Transactional
    public List<IntegrationConnection> findAll() {
        seedAvailableConnections();
        return repository.findByTenantIdOrderByProviderAsc(tenantContext.tenantId()).stream()
                .map(this::withOAuthAvailability)
                .toList();
    }

    /**
     * Tells the console whether Connect should send the browser through the
     * provider's OAuth flow or fall back to a demo registration. Set here rather
     * than persisted, because it depends on runtime configuration.
     */
    private IntegrationConnection withOAuthAvailability(IntegrationConnection connection) {
        connection.setOauthAvailable(providerOAuthClient.canStartOAuth(connection.getProvider()));
        return connection;
    }

    @Transactional
    public IntegrationConnection install(IntegrationProvider provider, IntegrationInstallRequest request) {
        seedAvailableConnections();
        if (request.state() != null && !request.state().isBlank() && !tenantContext.tenantId().equals(request.state())) {
            throw new IllegalArgumentException("Integration OAuth state does not match current tenant.");
        }
        IntegrationConnection connection = repository.findByTenantIdAndProvider(tenantContext.tenantId(), provider)
                .orElseThrow(() -> new IllegalArgumentException("Integration provider not available: " + provider));
        String externalAccount = request.externalAccount() == null || request.externalAccount().isBlank()
                ? defaultExternalAccount(provider)
                : request.externalAccount();
        String tokenSecretRef = connection.getTokenSecretRef();
        // Without provider credentials configured there is no OAuth exchange and no
        // token, so this connection cannot reach the provider. Say that plainly:
        // claiming a completed installation (and inventing record/latency figures)
        // presents an unconnected integration as a working one.
        String detail = "Demo connection: no OAuth exchange performed and no provider token stored. "
                + "Configure provider credentials and enable real exchange to connect a live account.";
        int recordsInspected = 0;
        int latencyMs = 0;
        // Derived from whether the exchange actually ran, not from tokenSecretRef:
        // seeded connections carry a placeholder ref that only looks like a
        // stored token, so that field cannot distinguish live from demo.
        boolean live = providerOAuthClient.shouldExchange(provider, request);
        if (live) {
            IntegrationOAuthResult exchange = providerOAuthClient.exchange(provider, request, externalAccount);
            externalAccount = exchange.externalAccount();
            tokenSecretRef = tokenVault.store(tenantContext.tenantId(), provider, exchange.accessToken(), exchange.refreshToken());
            detail = exchange.detail();
            recordsInspected = exchange.recordsInspected();
            latencyMs = exchange.latencyMs();
        }
        connection.connect(externalAccount, tokenSecretRef, detail);
        IntegrationConnection saved = repository.save(connection);
        // A demo install inspected nothing, so recording SUCCESS with a perfect
        // health score would put a green, healthy-looking row in the sync history
        // for a connection that never reached the provider.
        recordSync(
                saved,
                live ? IntegrationSyncStatus.SUCCESS : IntegrationSyncStatus.DEGRADED,
                recordsInspected,
                latencyMs,
                live ? 100 : 0,
                detail);
        audit(
                "INTEGRATION_CONNECTED",
                provider.name(),
                live
                        ? "Connected " + provider + " to " + externalAccount + "."
                        : "Registered demo " + provider + " connection for " + externalAccount
                                + " (no OAuth exchange).");
        return withOAuthAvailability(saved);
    }

    @Transactional
    public IntegrationConnection sync(long id) {
        IntegrationConnection connection = repository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + id));
        if (providerSignalSyncService.canSyncLive(connection)) {
            operationalMetrics.providerSyncAttempt(connection.getProvider(), "live");
            try {
                ProviderSyncResult result = providerSignalSyncService.sync(connection)
                        .orElseThrow(() -> new IllegalStateException("Live provider sync is not configured."));
                connection.sync(IntegrationSyncStatus.SUCCESS, result.healthScore(), result.detail());
                IntegrationConnection saved = repository.save(connection);
                recordSync(saved, IntegrationSyncStatus.SUCCESS, result.recordsInspected(), result.latencyMs(), result.healthScore(), result.detail());
                audit("INTEGRATION_LIVE_SYNCED", connection.getProvider().name(), result.detail());
                operationalEventLogger.info("integration.live_sync_succeeded", java.util.Map.of(
                        "provider", connection.getProvider(),
                        "connectionId", connection.getId(),
                        "recordsInspected", result.recordsInspected(),
                        "latencyMs", result.latencyMs()
                ));
                return saved;
            } catch (ProviderSyncException ex) {
                String detail = ex.category().name() + ": " + ex.getMessage();
                operationalMetrics.providerSyncFailure(connection.getProvider(), ex.category().name());
                operationalEventLogger.warn("integration.live_sync_failed", java.util.Map.of(
                        "provider", connection.getProvider(),
                        "connectionId", connection.getId(),
                        "category", ex.category().name(),
                        "message", ex.getMessage()
                ));
                connection.sync(IntegrationSyncStatus.FAILED, 35, detail);
                IntegrationConnection saved = repository.save(connection);
                recordSync(saved, IntegrationSyncStatus.FAILED, 0, 1, 35, detail);
                backgroundJobQueueService.enqueueProviderSyncRetry(saved, detail);
                audit("INTEGRATION_LIVE_SYNC_FAILED", connection.getProvider().name(), detail);
                return saved;
            } catch (Exception ex) {
                String detail = ex.getMessage() == null ? "Live provider sync failed." : ex.getMessage();
                operationalMetrics.providerSyncFailure(connection.getProvider(), "UNKNOWN");
                connection.sync(IntegrationSyncStatus.FAILED, 35, detail);
                IntegrationConnection saved = repository.save(connection);
                recordSync(saved, IntegrationSyncStatus.FAILED, 0, 1, 35, detail);
                backgroundJobQueueService.enqueueProviderSyncRetry(saved, detail);
                audit("INTEGRATION_LIVE_SYNC_FAILED", connection.getProvider().name(), detail);
                return saved;
            }
        }
        // Nothing was fetched, so nothing can be reported as fetched. This branch
        // used to hash the tenant and provider into a status and a record count
        // and announce "Latest sync completed successfully", which put green rows
        // describing work that never happened into the same history as real ones.
        operationalMetrics.providerSyncAttempt(connection.getProvider(), "unavailable");
        String detail = "No live provider connection, so nothing was fetched. "
                + "Connect this integration to sync real data.";
        connection.sync(IntegrationSyncStatus.DEGRADED, 0, detail);
        IntegrationConnection saved = repository.save(connection);
        recordSync(saved, IntegrationSyncStatus.DEGRADED, 0, 0, 0, detail);
        audit("INTEGRATION_SYNC_UNAVAILABLE", connection.getProvider().name(),
                "Sync requested for " + connection.getDisplayName() + " with no live connection.");
        operationalEventLogger.info("integration.sync_unavailable", java.util.Map.of(
                "provider", connection.getProvider(),
                "connectionId", connection.getId()
        ));
        return withOAuthAvailability(saved);
    }

    @Transactional
    public IntegrationConnection syncForJob(String tenantId, long id) {
        IntegrationConnection connection = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + id));
        operationalMetrics.providerSyncAttempt(connection.getProvider(), "job");
        // Retries only mean something against a live connection. Without one this
        // reported a hash-derived status and record count as a completed
        // background sync, which is where the "75 records" rows came from.
        if (!providerSignalSyncService.canSyncLive(connection)) {
            String unavailable = "No live provider connection, so the background sync fetched nothing.";
            connection.sync(IntegrationSyncStatus.DEGRADED, 0, unavailable);
            IntegrationConnection skipped = repository.save(connection);
            recordSync(skipped, IntegrationSyncStatus.DEGRADED, 0, 0, 0, unavailable);
            auditForTenant(skipped.getTenantId(), skipped.getOrganizationName(),
                    "BACKGROUND_PROVIDER_SYNC_SKIPPED", skipped.getProvider().name(), unavailable);
            return withOAuthAvailability(skipped);
        }

        long started = System.nanoTime();
        try {
            ProviderSyncResult result = providerSignalSyncService.sync(connection)
                    .orElseThrow(() -> new IllegalStateException("Live sync produced no result."));
            int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
            connection.sync(IntegrationSyncStatus.SUCCESS, 100, result.detail());
            IntegrationConnection saved = repository.save(connection);
            recordSync(saved, IntegrationSyncStatus.SUCCESS, result.recordsInspected(), latencyMs, 100, result.detail());
            auditForTenant(saved.getTenantId(), saved.getOrganizationName(),
                    "BACKGROUND_PROVIDER_SYNC", saved.getProvider().name(), result.detail());
            return withOAuthAvailability(saved);
        } catch (RuntimeException ex) {
            int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
            String failure = ex.getMessage() == null ? "Background provider sync failed." : ex.getMessage();
            operationalMetrics.providerSyncFailure(connection.getProvider(), "UNKNOWN");
            connection.sync(IntegrationSyncStatus.FAILED, 35, failure);
            IntegrationConnection saved = repository.save(connection);
            recordSync(saved, IntegrationSyncStatus.FAILED, 0, latencyMs, 35, failure);
            auditForTenant(saved.getTenantId(), saved.getOrganizationName(),
                    "BACKGROUND_PROVIDER_SYNC_FAILED", saved.getProvider().name(), failure);
            throw new IllegalStateException(failure, ex);
        }
    }

    /**
     * Sets which account or repository a connected integration reads from.
     *
     * <p>Separate from install because the OAuth round trip leaves no room to
     * ask: the browser goes to the provider and comes back with a code, so the
     * target has to be chosen afterwards. Reusing install for this would drop a
     * live connection back to a demo registration, since that call carries no
     * authorization code.
     */
    @Transactional
    public IntegrationConnection updateExternalAccount(long id, String externalAccount) {
        if (externalAccount == null || externalAccount.isBlank()) {
            throw new IllegalArgumentException("Account must not be blank.");
        }
        IntegrationConnection connection = repository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + id));
        String trimmed = externalAccount.trim();
        if (connection.getProvider() == IntegrationProvider.GITHUB && !trimmed.contains("/")) {
            throw new IllegalArgumentException("GitHub account must be owner/repository.");
        }
        connection.retarget(trimmed);
        IntegrationConnection saved = repository.save(connection);
        audit("INTEGRATION_RETARGETED", connection.getProvider().name(),
                "Pointed " + connection.getDisplayName() + " at " + trimmed + ".");
        return withOAuthAvailability(saved);
    }

    @Transactional
    public IntegrationConnection disconnect(long id) {
        IntegrationConnection connection = repository.findByIdAndTenantId(id, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + id));
        // Destroy the credential rather than leaving it encrypted in the vault.
        // Disconnecting is how a user revokes access, so the token must not
        // outlive it; a later reconnect stores a fresh one.
        tokenVault.purge(connection.getTokenSecretRef());
        connection.disconnect();
        IntegrationConnection saved = repository.save(connection);
        audit("INTEGRATION_DISCONNECTED", connection.getProvider().name(), "Disconnected " + connection.getDisplayName() + ".");
        return withOAuthAvailability(saved);
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncEvent> syncHistory() {
        return syncEventRepository.findTop30ByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId());
    }

    @Scheduled(fixedDelayString = "${sentinel.integrations.health-check-delay-ms:300000}")
    @Transactional
    public void refreshConnectedIntegrationHealth() {
        if (!runtimeModeService.workerEnabled()) {
            return;
        }
        repository.findByStatus(com.sentinelai.model.IntegrationStatus.CONNECTED).forEach(connection -> {
            // Only a connection holding a usable token can be checked. Anything
            // else would need its status invented, which is what this job used to
            // do: it hashed tenant, provider and the hour into a status, emitting
            // FAILED one time in twenty and DEGRADED three, then wrote a record
            // count and latency derived from the same hash. Integrations that had
            // never been contacted accumulated green history and occasional
            // failures, and once a real GitHub sync existed those invented rows
            // sat alongside it indistinguishable at a glance.
            if (!providerSignalSyncService.canSyncLive(connection)) {
                return;
            }
            long started = System.nanoTime();
            try {
                // Empty means no probe exists for this provider, so there is
                // nothing verified to report and the connection is left alone.
                Optional<Boolean> probe = providerSignalSyncService.checkReachable(connection);
                if (probe.isEmpty()) {
                    return;
                }
                boolean reachable = probe.get();
                int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
                IntegrationSyncStatus status = reachable ? IntegrationSyncStatus.SUCCESS : IntegrationSyncStatus.FAILED;
                String detail = reachable
                        ? "Provider reachable and credential accepted."
                        : "Provider rejected the stored credential. Reconnect this integration.";
                connection.sync(status, reachable ? 100 : 0, detail);
                IntegrationConnection saved = repository.save(connection);
                // Zero records: this checks the credential, it does not ingest.
                recordSync(saved, status, 0, latencyMs, reachable ? 100 : 0, detail);
            } catch (RuntimeException ex) {
                int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
                String detail = "Health check could not reach the provider: " + ex.getMessage();
                connection.sync(IntegrationSyncStatus.DEGRADED, 50, detail);
                IntegrationConnection saved = repository.save(connection);
                recordSync(saved, IntegrationSyncStatus.DEGRADED, 0, latencyMs, 50, detail);
            }
        });
    }

    private void seedAvailableConnections() {
        Arrays.stream(IntegrationProvider.values()).forEach(provider -> {
            IntegrationConnection connection = repository.findByTenantIdAndProvider(tenantContext.tenantId(), provider)
                    .orElseGet(() -> repository.save(template(provider)));
            connection.refreshConfiguration(
                    installUrl(provider),
                    scopes(provider),
                    "db/encrypted/" + tenantContext.tenantId() + "/" + provider.name().toLowerCase(Locale.US)
            );
        });
    }

    private IntegrationConnection template(IntegrationProvider provider) {
        return new IntegrationConnection(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                provider,
                displayName(provider),
                installUrl(provider),
                scopes(provider),
                "db/encrypted/" + tenantContext.tenantId() + "/" + provider.name().toLowerCase(Locale.US),
                "Ready to connect. OAuth tokens should be stored in Secrets Manager."
        );
    }

    private String displayName(IntegrationProvider provider) {
        return switch (provider) {
            case GITHUB -> "GitHub";
            case JIRA -> "Jira Cloud";
            case CI -> "CI Provider";
        };
    }

    private String installUrl(IntegrationProvider provider) {
        return switch (provider) {
            case GITHUB -> providerOAuthClient.installUrl(provider, "https://github.com/apps/sentinel-ai/installations/new");
            case JIRA -> providerOAuthClient.installUrl(provider, "https://marketplace.atlassian.com/apps/sentinel-ai");
            case CI -> providerOAuthClient.installUrl(provider, "https://sentinel.ai/integrations/ci");
        };
    }

    private String scopes(IntegrationProvider provider) {
        return switch (provider) {
            case GITHUB -> "repository:read, pull_request:read, checks:read, deployments:read";
            case JIRA -> "read:jira-work, read:jira-user, offline_access";
            case CI -> "build:read, artifacts:read, checks:read";
        };
    }

    private String defaultExternalAccount(IntegrationProvider provider) {
        String slug = tenantContext.organizationName().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        return switch (provider) {
            case GITHUB -> slug + "/engineering";
            case JIRA -> slug + ".atlassian.net";
            case CI -> slug + " delivery pipelines";
        };
    }




    private void recordSync(
            IntegrationConnection connection,
            IntegrationSyncStatus status,
            int recordsInspected,
            int latencyMs,
            int healthScore,
            String detail
    ) {
        syncEventRepository.save(new IntegrationSyncEvent(
                connection.getTenantId(),
                connection.getOrganizationName(),
                connection.getId(),
                connection.getProvider(),
                status,
                recordsInspected,
                latencyMs,
                healthScore,
                detail,
                Instant.now()
        ));
    }

    private void audit(String action, String target, String details) {
        auditForTenant(tenantContext.tenantId(), tenantContext.organizationName(), action, target, details);
    }

    private void auditForTenant(String tenantId, String organizationName, String action, String target, String details) {
        auditEventRepository.save(new AuditEvent(
                tenantId,
                organizationName,
                "sentinel-integrations",
                action,
                target,
                details + " requestId=" + RequestContext.requestId(),
                Instant.now()
        ));
    }
}
