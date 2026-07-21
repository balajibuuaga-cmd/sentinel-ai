package com.sentinelai.service.integrations;

import com.sentinelai.model.IntegrationInstallRequest;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Service
public class ProviderOAuthClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TenantContext tenantContext;
    private final boolean realExchangeEnabled;
    private final ProviderConfig github;
    private final ProviderConfig jira;
    private final ProviderConfig ci;

    public ProviderOAuthClient(
            ObjectMapper objectMapper,
            TenantContext tenantContext,
            @Value("${sentinel.integrations.real-exchange-enabled:false}") boolean realExchangeEnabled,
            @Value("${sentinel.integrations.github.client-id:}") String githubClientId,
            @Value("${sentinel.integrations.github.client-secret:}") String githubClientSecret,
            @Value("${sentinel.integrations.github.redirect-uri:}") String githubRedirectUri,
            @Value("${sentinel.integrations.jira.client-id:}") String jiraClientId,
            @Value("${sentinel.integrations.jira.client-secret:}") String jiraClientSecret,
            @Value("${sentinel.integrations.jira.redirect-uri:}") String jiraRedirectUri,
            @Value("${sentinel.integrations.ci.client-id:}") String ciClientId,
            @Value("${sentinel.integrations.ci.client-secret:}") String ciClientSecret,
            @Value("${sentinel.integrations.ci.redirect-uri:}") String ciRedirectUri,
            @Value("${sentinel.integrations.ci.authorize-url:}") String ciAuthorizeUrl,
            @Value("${sentinel.integrations.ci.token-url:}") String ciTokenUrl
    ) {
        this.objectMapper = objectMapper;
        this.tenantContext = tenantContext;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.realExchangeEnabled = realExchangeEnabled;
        this.github = new ProviderConfig(
                githubClientId,
                githubClientSecret,
                githubRedirectUri,
                "https://github.com/login/oauth/authorize",
                "https://github.com/login/oauth/access_token",
                "repo read:org read:user"
        );
        this.jira = new ProviderConfig(
                jiraClientId,
                jiraClientSecret,
                jiraRedirectUri,
                "https://auth.atlassian.com/authorize",
                "https://auth.atlassian.com/oauth/token",
                "read:jira-work read:jira-user offline_access"
        );
        this.ci = new ProviderConfig(
                ciClientId,
                ciClientSecret,
                ciRedirectUri,
                ciAuthorizeUrl,
                ciTokenUrl,
                "build:read artifacts:read checks:read"
        );
    }

    public boolean shouldExchange(IntegrationProvider provider, IntegrationInstallRequest request) {
        return realExchangeEnabled && !isBlank(request.code()) && config(provider).isConfigured();
    }

    /**
     * Whether sending the browser to this provider's authorize URL can lead to a
     * completed exchange. False means the Connect action would round-trip to the
     * provider and come back unable to swap the code for a token.
     */
    /**
     * Asks the provider who the token belongs to. Best-effort: a failure here
     * must not fail an otherwise successful exchange, so it returns blank and
     * lets the caller fall back.
     */
    private String authenticatedAccount(IntegrationProvider provider, String accessToken) {
        if (provider != IntegrationProvider.GITHUB) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.github.com/user"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            return objectMapper.readTree(response.body()).path("login").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    public boolean canStartOAuth(IntegrationProvider provider) {
        return realExchangeEnabled && config(provider).isConfigured();
    }

    public IntegrationOAuthResult exchange(IntegrationProvider provider, IntegrationInstallRequest request, String fallbackAccount) {
        ProviderConfig config = config(provider);
        long started = System.nanoTime();
        try {
            HttpRequest httpRequest = switch (provider) {
                case GITHUB -> githubTokenRequest(config, request.code());
                case JIRA, CI -> jsonTokenRequest(config, request.code());
            };
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Provider token exchange failed with status " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String accessToken = payload.path("access_token").asText("");
            if (isBlank(accessToken)) {
                throw new IllegalStateException("Provider did not return an access token");
            }
            String refreshToken = payload.path("refresh_token").asText("");
            // "scope" was in this chain and won for GitHub, whose token response
            // carries no account_id: the connection ended up named
            // "read:org,read:user,repo" and every sync then failed because that
            // is not an owner/repository. A scope list never identifies an
            // account, so resolve the real one from the provider instead.
            String account = firstNonBlank(
                    request.externalAccount(),
                    payload.path("account_id").asText(""),
                    authenticatedAccount(provider, accessToken),
                    fallbackAccount);
            return new IntegrationOAuthResult(
                    account,
                    accessToken,
                    refreshToken,
                    1,
                    elapsedMs(started),
                    "OAuth authorization code exchanged successfully. Token encrypted in Sentinel's integration vault."
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to exchange " + provider + " OAuth code", ex);
        }
    }

    public String installUrl(IntegrationProvider provider, String fallback) {
        ProviderConfig config = config(provider);
        if (!config.hasAuthorizeUrl()) {
            return fallback;
        }
        String state = tenantContext.tenantId();
        if (provider == IntegrationProvider.JIRA) {
            return config.authorizeUrl()
                    + "?audience=api.atlassian.com"
                    + "&client_id=" + encode(config.clientId())
                    + "&scope=" + encode(config.scope())
                    + "&redirect_uri=" + encode(config.redirectUri())
                    + "&state=" + encode(state)
                    + "&response_type=code"
                    + "&prompt=consent";
        }
        return config.authorizeUrl()
                + "?client_id=" + encode(config.clientId())
                + "&scope=" + encode(config.scope())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&state=" + encode(state)
                + "&response_type=code";
    }

    private HttpRequest githubTokenRequest(ProviderConfig config, String code) {
        String body = form("client_id", config.clientId())
                + "&" + form("client_secret", config.clientSecret())
                + "&" + form("code", code)
                + "&" + form("redirect_uri", config.redirectUri());
        return HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl()))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpRequest jsonTokenRequest(ProviderConfig config, String code) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "grant_type", "authorization_code",
                "client_id", config.clientId(),
                "client_secret", config.clientSecret(),
                "code", code,
                "redirect_uri", config.redirectUri()
        ));
        return HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl()))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private ProviderConfig config(IntegrationProvider provider) {
        return switch (provider) {
            case GITHUB -> github;
            case JIRA -> jira;
            case CI -> ci;
        };
    }

    private String form(String key, String value) {
        return encode(key) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private int elapsedMs(long startedNanos) {
        return Math.max(1, (int) ((System.nanoTime() - startedNanos) / 1_000_000));
    }

    private String firstNonBlank(String... values) {
        return java.util.Arrays.stream(values)
                .filter(value -> !isBlank(value))
                .findFirst()
                .orElse("");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProviderConfig(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizeUrl,
            String tokenUrl,
            String scope
    ) {
        private boolean isConfigured() {
            return !isBlank(clientId) && !isBlank(clientSecret) && !isBlank(redirectUri) && !isBlank(tokenUrl);
        }

        private boolean hasAuthorizeUrl() {
            return !isBlank(clientId) && !isBlank(redirectUri) && !isBlank(authorizeUrl);
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
