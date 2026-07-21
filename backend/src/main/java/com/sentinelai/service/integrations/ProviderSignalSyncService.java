package com.sentinelai.service.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.model.CiSignalRequest;
import com.sentinelai.model.GitHubWebhookRequest;
import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.JiraSignalRequest;
import com.sentinelai.service.EngineeringSignalIngestionService;
import com.sentinelai.service.GitHubWebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProviderSignalSyncService {

    private final ObjectMapper objectMapper;
    private final IntegrationTokenVault tokenVault;
    private final GitHubWebhookService gitHubWebhookService;
    private final EngineeringSignalIngestionService engineeringSignalIngestionService;
    private final HttpClient httpClient;
    private final boolean realExchangeEnabled;
    private final String jiraCloudId;
    private final String ciRunsUrl;

    public ProviderSignalSyncService(
            ObjectMapper objectMapper,
            IntegrationTokenVault tokenVault,
            GitHubWebhookService gitHubWebhookService,
            EngineeringSignalIngestionService engineeringSignalIngestionService,
            @Value("${sentinel.integrations.real-exchange-enabled:false}") boolean realExchangeEnabled,
            @Value("${sentinel.integrations.jira.cloud-id:}") String jiraCloudId,
            @Value("${sentinel.integrations.ci.runs-url:}") String ciRunsUrl
    ) {
        this.objectMapper = objectMapper;
        this.tokenVault = tokenVault;
        this.gitHubWebhookService = gitHubWebhookService;
        this.engineeringSignalIngestionService = engineeringSignalIngestionService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.realExchangeEnabled = realExchangeEnabled;
        this.jiraCloudId = jiraCloudId;
        this.ciRunsUrl = ciRunsUrl;
    }

    /**
     * A live sync needs a token the vault can actually hand back. The secret ref
     * cannot answer that: {@code IntegrationTokenVault.store} derives it from
     * tenant and provider, so it is identical to the placeholder every
     * never-connected integration carries. Checking the ref's prefix therefore
     * reported demo connections as syncable, and the sync failed with a raw
     * "Unable to decrypt integration token" instead of saying it was never
     * connected.
     */
    public boolean canSyncLive(IntegrationConnection connection) {
        return realExchangeEnabled
                && tokenVault.usableAccessToken(connection.getTokenSecretRef()).isPresent();
    }

    /**
     * Asks the provider whether the stored credential still works, without
     * ingesting anything.
     *
     * <p>Returns empty when no probe exists for the provider, so a caller reports
     * nothing rather than assuming health it did not verify. Only GitHub has a
     * cheap endpoint wired up here; the others would need a real call each, and
     * inventing a verdict for them is the behaviour this replaced.
     */
    public Optional<Boolean> checkReachable(IntegrationConnection connection) {
        String token = tokenVault.usableAccessToken(connection.getTokenSecretRef()).orElse("");
        if (token.isBlank() || connection.getProvider() != IntegrationProvider.GITHUB) {
            return Optional.empty();
        }
        try {
            getJson("https://api.github.com/user", token);
            return Optional.of(true);
        } catch (Exception ex) {
            return Optional.of(false);
        }
    }

    public Optional<ProviderSyncResult> sync(IntegrationConnection connection) {
        if (!realExchangeEnabled) {
            return Optional.empty();
        }
        String token = tokenVault.usableAccessToken(connection.getTokenSecretRef()).orElse("");
        if (token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(switch (connection.getProvider()) {
            case GITHUB -> syncGitHub(connection, token);
            case JIRA -> syncJira(connection, token);
            case CI -> syncCi(connection, token);
        });
    }

    private ProviderSyncResult syncGitHub(IntegrationConnection connection, String token) {
        long started = System.nanoTime();
        try {
            String repository = connection.getExternalAccount();
            if (repository == null || !repository.contains("/")) {
                throw new IllegalStateException("GitHub external account must be owner/repository.");
            }
            JsonNode pulls = getJson("https://api.github.com/repos/" + repository + "/pulls?state=open&per_page=5", token);
            int count = pulls.isArray() ? pulls.size() : 0;
            int ingested = 0;
            for (JsonNode pull : pulls) {
                String title = pull.path("title").asText("GitHub pull request");
                String branch = pull.path("head").path("sha").asText("github-sync");
                String actor = pull.path("user").path("login").asText("github-sync");
                gitHubWebhookService.ingest(new GitHubWebhookRequest(
                        repository,
                        serviceName(repository),
                        "GitHub",
                        "production",
                        branch,
                        title,
                        actor,
                        "unknown",
                        List.of("provider-sync/github-pr"),
                        List.of()
                ));
                ingested++;
            }
            return new ProviderSyncResult(count, elapsedMs(started), count == 0 ? 92 : 98, "GitHub sync ingested " + ingested + " open pull requests.");
        } catch (Exception ex) {
            throw categorized("GitHub live sync failed", ex);
        }
    }

    private ProviderSyncResult syncJira(IntegrationConnection connection, String token) {
        long started = System.nanoTime();
        try {
            if (jiraCloudId == null || jiraCloudId.isBlank()) {
                throw new IllegalStateException("Jira cloud id is not configured.");
            }
            String jql = java.net.URLEncoder.encode("priority in (Blocker,Critical,High) AND updated >= -14d ORDER BY updated DESC", java.nio.charset.StandardCharsets.UTF_8);
            JsonNode response = getJson("https://api.atlassian.com/ex/jira/" + jiraCloudId + "/rest/api/3/search?maxResults=5&jql=" + jql, token);
            JsonNode issues = response.path("issues");
            int count = issues.isArray() ? issues.size() : 0;
            int ingested = 0;
            for (JsonNode issue : issues) {
                JsonNode fields = issue.path("fields");
                engineeringSignalIngestionService.ingestJira(new JiraSignalRequest(
                        issue.path("key").asText("JIRA-SYNC"),
                        fields.path("summary").asText("Jira issue from provider sync"),
                        fields.path("priority").path("name").asText("High"),
                        fields.path("status").path("name").asText("In Progress"),
                        fields.path("issuetype").path("name").asText("Bug"),
                        serviceName(connection.getExternalAccount()),
                        "Jira",
                        "production",
                        null,
                        "jira-sync",
                        labels(fields.path("labels")),
                        List.of()
                ));
                ingested++;
            }
            return new ProviderSyncResult(count, elapsedMs(started), count == 0 ? 94 : 90, "Jira sync ingested " + ingested + " high-priority work items.");
        } catch (Exception ex) {
            throw categorized("Jira live sync failed", ex);
        }
    }

    private ProviderSyncResult syncCi(IntegrationConnection connection, String token) {
        long started = System.nanoTime();
        try {
            if (ciRunsUrl == null || ciRunsUrl.isBlank()) {
                throw new IllegalStateException("CI runs URL is not configured.");
            }
            JsonNode response = getJson(ciRunsUrl, token);
            JsonNode runs = response.isArray() ? response : response.path("runs");
            int count = runs.isArray() ? runs.size() : 0;
            int ingested = 0;
            for (JsonNode run : runs) {
                String status = run.path("status").asText(run.path("conclusion").asText("unknown"));
                engineeringSignalIngestionService.ingestCi(new CiSignalRequest(
                        connection.getDisplayName(),
                        run.path("repository").asText(connection.getExternalAccount()),
                        serviceName(run.path("repository").asText(connection.getExternalAccount())),
                        run.path("ownerTeam").asText("CI"),
                        run.path("environment").asText("production"),
                        run.path("commitSha").asText("ci-sync"),
                        run.path("name").asText("provider-sync"),
                        run.path("url").asText(""),
                        status,
                        run.path("failedTests").asInt(0),
                        run.path("coverageDelta").asInt(0),
                        "ci-sync",
                        List.of(),
                        List.of()
                ));
                ingested++;
            }
            return new ProviderSyncResult(count, elapsedMs(started), count == 0 ? 94 : 96, "CI sync ingested " + ingested + " pipeline runs.");
        } catch (Exception ex) {
            throw categorized("CI live sync failed", ex);
        }
    }

    private JsonNode getJson(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Provider returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private List<String> labels(JsonNode labelsNode) {
        List<String> labels = new ArrayList<>();
        if (labelsNode.isArray()) {
            labelsNode.forEach(label -> labels.add(label.asText()));
        }
        return labels;
    }

    private String serviceName(String source) {
        if (source == null || source.isBlank()) {
            return "provider-sync-service";
        }
        String candidate = source.contains("/") ? source.substring(source.lastIndexOf('/') + 1) : source;
        return candidate.toLowerCase(Locale.US).replaceAll("[^a-z0-9-]+", "-");
    }

    private int elapsedMs(long startedNanos) {
        return Math.max(1, (int) ((System.nanoTime() - startedNanos) / 1_000_000));
    }

    private ProviderSyncException categorized(String prefix, Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return new ProviderSyncException(classify(message), prefix + ": " + message, ex);
    }

    private ProviderSyncFailureCategory classify(String message) {
        String normalized = message.toLowerCase(Locale.US);
        if (normalized.contains("401") || normalized.contains("unauthorized") || normalized.contains("expired")) {
            return ProviderSyncFailureCategory.AUTH_EXPIRED;
        }
        if (normalized.contains("403") || normalized.contains("scope") || normalized.contains("permission")) {
            return ProviderSyncFailureCategory.MISSING_SCOPE;
        }
        if (normalized.contains("429") || normalized.contains("rate")) {
            return ProviderSyncFailureCategory.RATE_LIMITED;
        }
        if (normalized.contains("500") || normalized.contains("502") || normalized.contains("503") || normalized.contains("504")) {
            return ProviderSyncFailureCategory.PROVIDER_DOWN;
        }
        if (normalized.contains("not configured") || normalized.contains("must be")) {
            return ProviderSyncFailureCategory.BAD_CONFIG;
        }
        return ProviderSyncFailureCategory.UNKNOWN;
    }
}
