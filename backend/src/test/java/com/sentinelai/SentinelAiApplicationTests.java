package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.repository.IntegrationTokenSecretRepository;
import com.sentinelai.security.AuthenticatedUser;
import com.sentinelai.security.CognitoJwtValidator;
import com.sentinelai.security.DemoUser;
import com.sentinelai.security.JwtService;
import com.sentinelai.security.TokenAuthenticationService;
import com.sentinelai.service.integrations.IntegrationTokenVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret"
        }
)
@AutoConfigureMockMvc
class SentinelAiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationTokenVault integrationTokenVault;

    @Autowired
    private IntegrationTokenSecretRepository integrationTokenSecretRepository;

    @Test
    void flagshipDemoFlowWorksEndToEnd() throws Exception {
        JsonNode authStatus = getJsonWithoutAuth("/api/auth/status");
        assertThat(authStatus.get("mode").asText()).isEqualTo("demo");
        assertThat(authStatus.get("demoLoginEnabled").asBoolean()).isTrue();
        assertThat(authStatus.has("cognitoLoginUrl")).isTrue();

        String token = login();

        JsonNode briefing = getJson("/api/briefing/executive", token);
        assertThat(briefing.get("chiefBriefing").asText()).contains("Executive briefing");

        JsonNode provider = getJson("/api/ai/provider", token);
        assertThat(provider.get("activeProvider").asText()).isEqualTo("deterministic-chief-engineer");
        assertThat(provider.get("externalCallsEnabled").asBoolean()).isFalse();

        JsonNode operatorConsole = getJson("/api/operator/console", token);
        assertThat(operatorConsole.get("requestId").asText()).isNotBlank();
        assertThat(operatorConsole.get("tenantId").asText()).isEqualTo("sentinel-demo");
        assertThat(operatorConsole.get("runtimeMode").asText()).isEqualTo("combined");
        assertThat(operatorConsole.get("apiEnabled").asBoolean()).isTrue();
        assertThat(operatorConsole.get("workerEnabled").asBoolean()).isTrue();
        assertThat(operatorConsole.get("authMode").asText()).isEqualTo("demo");
        assertThat(operatorConsole.get("integrationMode").asText()).isEqualTo("simulated provider sync");
        assertThat(operatorConsole.get("readinessStatus").asText()).isEqualTo("ready");
        assertThat(operatorConsole.get("metrics").has("deploymentReviewsCreated")).isTrue();
        assertThat(operatorConsole.get("recentFailures")).isNotNull();
        assertThat(operatorConsole.get("rateLimitBackend").asText()).isEqualTo("memory");
        assertThat(operatorConsole.get("redisRateLimitingEnabled").asBoolean()).isFalse();
        assertThat(operatorConsole.get("backgroundJobs").get("queued").asLong()).isGreaterThanOrEqualTo(0);

        mockMvc.perform(get("/api/ai/provider")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful())
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-Request-ID")).isNotBlank())
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isNotBlank())
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-RateLimit-Backend")).isEqualTo("memory"));

        MvcResult correlated = mockMvc.perform(get("/api/ai/provider")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-ID", "test-request-123"))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        assertThat(correlated.getResponse().getHeader("X-Request-ID")).isEqualTo("test-request-123");

        JsonNode integrations = getJson("/api/integration-connections", token);
        assertThat(integrations).hasSize(3);
        JsonNode githubConnection = postJson("/api/integration-connections/GITHUB/install", token, Map.of(
                "externalAccount", "sentinel-ai/engineering",
                "code", "demo-github-code",
                "state", "sentinel-demo"
        ));
        assertThat(githubConnection.get("status").asText()).isEqualTo("CONNECTED");
        JsonNode jiraConnection = postJson("/api/integration-connections/JIRA/install", token, Map.of(
                "externalAccount", "sentinel-ai.atlassian.net",
                "code", "demo-jira-code",
                "state", "sentinel-demo"
        ));
        assertThat(jiraConnection.get("status").asText()).isEqualTo("CONNECTED");
        JsonNode ciConnection = postJson("/api/integration-connections/CI/install", token, Map.of(
                "externalAccount", "Sentinel AI delivery pipelines",
                "code", "demo-ci-code",
                "state", "sentinel-demo"
        ));
        assertThat(ciConnection.get("status").asText()).isEqualTo("CONNECTED");

        JsonNode syncedConnection = postJson("/api/integration-connections/" + githubConnection.get("id").asLong() + "/sync", token, Map.of());
        assertThat(syncedConnection.get("lastSyncAt").asText()).isNotBlank();
        assertThat(syncedConnection.get("healthScore").asInt()).isGreaterThan(0);

        JsonNode syncHistory = getJson("/api/integration-connections/sync-history", token);
        assertThat(syncHistory).hasSizeGreaterThan(0);

        JsonNode deployments = getJson("/api/deployments", token);
        assertThat(deployments).hasSizeGreaterThan(0);
        long deploymentId = deployments.get(0).get("id").asLong();

        JsonNode memory = getJson("/api/briefing/memory/" + deploymentId, token);
        assertThat(memory.get("events")).hasSizeGreaterThan(0);
        assertThat(memory.get("confidence").asInt()).isGreaterThan(0);

        JsonNode prReview = postJson("/api/pr-reviews/simulate", token, Map.of(
                "repository", "sentinel-ai/payment-api",
                "prNumber", 418,
                "title", "Refactor payment authorization and settlement retry path",
                "author", "david",
                "serviceName", "payment-api",
                "ownerTeam", "Payments Platform",
                "ciStatus", "failure",
                "changedFiles", List.of(
                        "src/payments/AuthorizePayment.java",
                        "src/payments/SettlementRetryPolicy.java",
                        "db/migration/V43__payment_authorization_index.sql"
                )
        ));
        assertThat(prReview.get("recommendation").asText()).isIn("MERGE", "WAIT", "BLOCK");
        assertThat(prReview.get("riskScore").asInt()).isGreaterThan(0);

        JsonNode architecture = getJson("/api/architecture/brain", token);
        assertThat(architecture.get("serviceCount").asInt()).isGreaterThan(0);
        assertThat(architecture.get("riskCount").asInt()).isGreaterThan(0);

        JsonNode playbooks = getJson("/api/playbooks", token);
        assertThat(playbooks).hasSize(6);
        assertThat(playbooks.get(0).get("title").asText()).contains("Production Backend");
        JsonNode backendReadiness = getJson("/api/playbooks/backend-readiness", token);
        assertThat(backendReadiness.get("overallScore").asInt()).isGreaterThanOrEqualTo(80);
        assertThat(backendReadiness.get("checks")).hasSizeGreaterThanOrEqualTo(10);
        assertThat(backendReadiness.get("summary").asText()).contains("backend shipping checklist");
        assertThat(backendReadiness.get("nextActions")).hasSizeGreaterThan(0);
        JsonNode playbookAnswer = postJson("/api/ai/command", token, Map.of(
                "command", "Use the backend production checklist and security playbook. What should we check next?",
                "deploymentId", deploymentId
        ));
        assertThat(playbookAnswer.get("answer").asText()).contains("engineering playbooks");
        assertThat(playbookAnswer.get("answer").asText()).contains("current risk");

        JsonNode incidents = getJson("/api/incidents", token);
        assertThat(incidents).hasSizeGreaterThan(0);
        JsonNode firstIncident = incidents.get(0);
        assertThat(firstIncident.get("incidentKey").asText()).startsWith("INC-sentinel-demo-");
        assertThat(firstIncident.get("commanderBrief").asText()).contains("active engineering incident");
        assertThat(firstIncident.get("recommendedAction").asText()).isNotBlank();
        assertThat(firstIncident.get("timeline")).hasSizeGreaterThan(0);

        JsonNode mitigatingIncident = postJson("/api/incidents/" + firstIncident.get("id").asLong() + "/status", token, Map.of(
                "status", "MITIGATING",
                "actor", "release@sentinel.ai",
                "note", "Mitigation started from test flow."
        ));
        assertThat(mitigatingIncident.get("status").asText()).isEqualTo("MITIGATING");
        assertThat(mitigatingIncident.get("timeline")).hasSizeGreaterThan(firstIncident.get("timeline").size());

        JsonNode jobs = getJson("/api/jobs", token);
        assertThat(jobs).hasSizeGreaterThan(0);
        assertThat(jobs.get(0).get("targetType").asText()).isIn("incident", "integration_connection");
        JsonNode retriedJob = postJson("/api/jobs/" + jobs.get(0).get("id").asLong() + "/retry", token, Map.of());
        assertThat(retriedJob.get("status").asText()).isEqualTo("QUEUED");

        JsonNode webhookDeployment = postJson("/api/webhooks/github/simulate", token, Map.of(
                "repository", "sentinel-ai/payment-api",
                "serviceName", "payment-api",
                "ownerTeam", "Payments Platform",
                "environment", "production",
                "commitSha", "abc1234",
                "pullRequestTitle", "Update payment settlement migration",
                "actor", "release@sentinel.ai",
                "ciStatus", "failure",
                "changedFiles", List.of(
                        "src/payments/SettlementWriter.java",
                        "db/migration/V44__settlement_status.sql"
                ),
                "dependencies", List.of("checkout-service", "billing-service", "customer-ledger")
        ));
        assertThat(webhookDeployment.get("deploymentKey").asText()).startsWith("GH-");

        String signedWebhookPayload = objectMapper.writeValueAsString(Map.of(
                "repository", "sentinel-ai/order-api",
                "serviceName", "order-api",
                "ownerTeam", "Orders Platform",
                "environment", "production",
                "commitSha", "def5678",
                "pullRequestTitle", "Signed webhook release signal",
                "actor", "github-webhook",
                "ciStatus", "success",
                "changedFiles", List.of("src/orders/OrderController.java"),
                "dependencies", List.of("payment-api")
        ));
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Delivery", "signed-delivery-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", githubSignature(signedWebhookPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedWebhookPayload))
                .andExpect(status().is2xxSuccessful());

        String badWebhookPayload = "{";
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Delivery", "signed-delivery-bad")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", githubSignature(badWebhookPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badWebhookPayload))
                .andExpect(status().isBadRequest());

        JsonNode webhookDeliveries = getJson("/api/webhooks/deliveries", token);
        assertThat(webhookDeliveries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(webhookDeliveries.findValuesAsText("externalDeliveryId")).contains("signed-delivery-1", "signed-delivery-bad");
        JsonNode failedDelivery = findByText(webhookDeliveries, "externalDeliveryId", "signed-delivery-bad");
        assertThat(failedDelivery.get("status").asText()).isEqualTo("FAILED");
        assertThat(failedDelivery.get("maxReplayAttempts").asInt()).isEqualTo(3);
        assertThat(failedDelivery.get("replayEligibility").asText()).isEqualTo("ready");
        JsonNode replayQueued = postJson("/api/webhooks/deliveries/" + failedDelivery.get("id").asLong() + "/replay", token, Map.of());
        assertThat(replayQueued.get("status").asText()).isEqualTo("REPLAY_QUEUED");
        assertThat(replayQueued.get("replayEligibility").asText()).isEqualTo("queued");
        mockMvc.perform(post("/api/webhooks/deliveries/" + failedDelivery.get("id").asLong() + "/replay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        JsonNode webhookReplayJobs = getJson("/api/jobs", token);
        assertThat(webhookReplayJobs.findValuesAsText("jobType")).contains("WEBHOOK_REPLAY");

        JsonNode ciDeployment = postJson("/api/integrations/ci/simulate", token, Map.ofEntries(
                Map.entry("provider", "GitHub Actions"),
                Map.entry("repository", "sentinel-ai/payment-api"),
                Map.entry("serviceName", "payment-api"),
                Map.entry("ownerTeam", "Payments Platform"),
                Map.entry("environment", "production"),
                Map.entry("commitSha", "abc1234"),
                Map.entry("pipelineName", "payment-regression"),
                Map.entry("status", "failure"),
                Map.entry("failedTests", 6),
                Map.entry("coverageDelta", -14),
                Map.entry("actor", "release@sentinel.ai"),
                Map.entry("failedSuites", List.of("CheckoutRegression", "LedgerSettlementIT")),
                Map.entry("dependencies", List.of("checkout-service", "billing-service", "customer-ledger"))
        ));
        assertThat(ciDeployment.get("id").asLong()).isEqualTo(webhookDeployment.get("id").asLong());
        assertThat(ciDeployment.get("signals")).hasSizeGreaterThan(webhookDeployment.get("signals").size());

        JsonNode jiraDeployment = postJson("/api/integrations/jira/simulate", token, Map.ofEntries(
                Map.entry("issueKey", "PAY-912"),
                Map.entry("summary", "Customer-impacting payment capture defect requires settlement retry changes"),
                Map.entry("priority", "Critical"),
                Map.entry("status", "In QA"),
                Map.entry("issueType", "Incident"),
                Map.entry("serviceName", "payment-api"),
                Map.entry("ownerTeam", "Payments Platform"),
                Map.entry("environment", "production"),
                Map.entry("commitSha", "abc1234"),
                Map.entry("actor", "release@sentinel.ai"),
                Map.entry("labels", List.of("hotfix", "customer-impact")),
                Map.entry("dependencies", List.of("checkout-service", "billing-service", "customer-ledger"))
        ));
        assertThat(jiraDeployment.get("id").asLong()).isEqualTo(webhookDeployment.get("id").asLong());
        assertThat(jiraDeployment.get("riskAssessment").get("score").asInt()).isGreaterThanOrEqualTo(ciDeployment.get("riskAssessment").get("score").asInt());

        JsonNode blocked = postJson("/api/deployments/" + webhookDeployment.get("id").asLong() + "/approval", token, Map.of(
                "decision", "BLOCK",
                "approver", "release@sentinel.ai",
                "note", "Blocked by demo integration test."
        ));
        assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");

        JsonNode auditEvents = getJson("/api/audit-events", token);
        assertThat(auditEvents).hasSizeGreaterThan(0);
    }

    @Test
    void tenantScopedWorkspaceDoesNotExposeDefaultDemoData() throws Exception {
        String defaultToken = login("release@sentinel.ai", "sentinel-release");
        String acmeToken = login("acme-release@sentinel.ai", "sentinel-acme");

        JsonNode defaultProfile = getJson("/api/organization/current", defaultToken);
        assertThat(defaultProfile.get("tenantId").asText()).isEqualTo("sentinel-demo");
        assertThat(defaultProfile.get("deploymentCount").asInt()).isGreaterThan(0);
        int defaultStartingIntegrations = defaultProfile.get("connectedIntegrationCount").asInt();

        JsonNode acmeProfile = getJson("/api/organization/current", acmeToken);
        assertThat(acmeProfile.get("tenantId").asText()).isEqualTo("acme-payments");
        assertThat(acmeProfile.get("organizationName").asText()).isEqualTo("Acme Payments");
        assertThat(acmeProfile.get("deploymentCount").asInt()).isZero();
        assertThat(acmeProfile.get("connectedIntegrationCount").asInt()).isZero();

        JsonNode acmeDeployments = getJson("/api/deployments", acmeToken);
        assertThat(acmeDeployments).hasSize(0);

        JsonNode acmeGithubConnection = postJson("/api/integration-connections/GITHUB/install", acmeToken, Map.of(
                "externalAccount", "acme/payments",
                "code", "acme-github-code",
                "state", "acme-payments"
        ));
        assertThat(acmeGithubConnection.get("status").asText()).isEqualTo("CONNECTED");

        postJson("/api/webhooks/github/simulate", acmeToken, Map.of(
                "repository", "acme/payments",
                "serviceName", "payment-api",
                "ownerTeam", "Acme Payments Platform",
                "environment", "production",
                "commitSha", "acme123",
                "pullRequestTitle", "Change Acme payment capture migration",
                "actor", "acme-release@sentinel.ai",
                "ciStatus", "failure",
                "changedFiles", List.of("db/migration/V1__capture.sql"),
                "dependencies", List.of("checkout-service")
        ));

        JsonNode updatedAcmeProfile = getJson("/api/organization/current", acmeToken);
        assertThat(updatedAcmeProfile.get("deploymentCount").asInt()).isEqualTo(1);
        assertThat(updatedAcmeProfile.get("connectedIntegrationCount").asInt()).isEqualTo(1);

        JsonNode updatedDefaultProfile = getJson("/api/organization/current", defaultToken);
        assertThat(updatedDefaultProfile.get("deploymentCount").asInt()).isEqualTo(defaultProfile.get("deploymentCount").asInt());
        assertThat(updatedDefaultProfile.get("connectedIntegrationCount").asInt()).isEqualTo(defaultStartingIntegrations);
    }

    @Test
    void integrationCanDisconnectAndKeepHistory() throws Exception {
        String token = login("acme-release@sentinel.ai", "sentinel-acme");
        JsonNode connection = postJson("/api/integration-connections/CI/install", token, Map.of(
                "externalAccount", "Acme CI",
                "code", "ci-code",
                "state", "acme-payments"
        ));
        JsonNode disconnected = deleteJson("/api/integration-connections/" + connection.get("id").asLong(), token);
        assertThat(disconnected.get("status").asText()).isEqualTo("DISCONNECTED");
        JsonNode history = getJson("/api/integration-connections/sync-history", token);
        assertThat(history).hasSizeGreaterThan(0);
    }

    @Test
    void integrationTokenVaultEncryptsProviderTokensAtRest() {
        String secretRef = integrationTokenVault.store(
                "sentinel-demo",
                IntegrationProvider.GITHUB,
                "gho_live_provider_token",
                "refresh-token"
        );

        var stored = integrationTokenSecretRepository.findBySecretRef(secretRef).orElseThrow();

        assertThat(secretRef).isEqualTo("db/encrypted/sentinel-demo/github");
        assertThat(stored.getEncryptedAccessToken()).doesNotContain("gho_live_provider_token");
        assertThat(integrationTokenVault.accessToken(secretRef)).contains("gho_live_provider_token");
        assertThat(stored.getTokenFingerprint()).hasSize(16);
    }

    @Test
    void apiErrorsAreStructuredAndCorrelated() throws Exception {
        String token = login();
        MvcResult validation = mockMvc.perform(post("/api/integrations/ci/simulate")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-ID", "bad-ci-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("provider", ""))))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode validationBody = objectMapper.readTree(validation.getResponse().getContentAsString());
        assertThat(validation.getResponse().getHeader("X-Request-ID")).isEqualTo("bad-ci-request");
        assertThat(validationBody.get("requestId").asText()).isEqualTo("bad-ci-request");
        assertThat(validationBody.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(validationBody.get("details").has("repository")).isTrue();

        MvcResult badState = mockMvc.perform(post("/api/integration-connections/GITHUB/install")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-ID", "bad-state-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "externalAccount", "sentinel-ai/engineering",
                                "code", "demo-code",
                                "state", "other-tenant"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode badStateBody = objectMapper.readTree(badState.getResponse().getContentAsString());
        assertThat(badState.getResponse().getHeader("X-Request-ID")).isEqualTo("bad-state-request");
        assertThat(badStateBody.get("requestId").asText()).isEqualTo("bad-state-request");
        assertThat(badStateBody.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(badStateBody.get("message").asText()).contains("OAuth state");
    }

    @Test
    void cognitoValidatorAcceptsSignedTenantToken() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String kid = "test-cognito-key";
        String issuer = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST";
        CognitoJwtValidator validator = CognitoJwtValidator.withJwks(
                objectMapper,
                issuer,
                "sentinel-client",
                "custom:role",
                "custom:tenant_id",
                "custom:organization_name",
                jwksJson(kid, (RSAPublicKey) keyPair.getPublic())
        );
        String token = signedRs256Jwt(keyPair, kid, Map.ofEntries(
                Map.entry("iss", issuer),
                Map.entry("aud", "sentinel-client"),
                Map.entry("token_use", "id"),
                Map.entry("sub", "user-123"),
                Map.entry("email", "admin@customer.test"),
                Map.entry("custom:role", "admin"),
                Map.entry("custom:tenant_id", "customer-prod"),
                Map.entry("custom:organization_name", "Customer Prod"),
                Map.entry("exp", Instant.now().plusSeconds(300).getEpochSecond())
        ));

        Optional<AuthenticatedUser> user = validator.validate(token);

        assertThat(user).isPresent();
        assertThat(user.get().username()).isEqualTo("admin@customer.test");
        assertThat(user.get().role()).isEqualTo("ADMIN");
        assertThat(user.get().tenantId()).isEqualTo("customer-prod");
        assertThat(user.get().organizationName()).isEqualTo("Customer Prod");
    }

    @Test
    void hybridAuthAcceptsBothLocalAndCognitoTokensSideBySide() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String kid = "hybrid-test-key";
        String issuer = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_HYBRIDTEST";
        CognitoJwtValidator cognitoJwtValidator = CognitoJwtValidator.withJwks(
                objectMapper,
                issuer,
                "hybrid-client",
                "custom:role",
                "custom:tenant_id",
                "custom:organization_name",
                jwksJson(kid, (RSAPublicKey) keyPair.getPublic())
        );
        JwtService jwtService = new JwtService(objectMapper, "hybrid-test-secret-with-enough-length");
        TokenAuthenticationService tokenAuthenticationService = new TokenAuthenticationService(
                objectMapper,
                jwtService,
                cognitoJwtValidator,
                "hybrid-client",
                "https://sentinel-ai-145026616632.auth.us-east-1.amazoncognito.com",
                "https://3-90-3-12.nip.io/",
                "https://3-90-3-12.nip.io/"
        );

        String localToken = jwtService.issue(new DemoUser("local-user@sentinel.ai", "", "ADMIN", "local-tenant", "Local Co"));
        Optional<AuthenticatedUser> localUser = tokenAuthenticationService.validate(localToken);
        assertThat(localUser).isPresent();
        assertThat(localUser.get().username()).isEqualTo("local-user@sentinel.ai");
        assertThat(localUser.get().tenantId()).isEqualTo("local-tenant");

        String cognitoToken = signedRs256Jwt(keyPair, kid, Map.ofEntries(
                Map.entry("iss", issuer),
                Map.entry("aud", "hybrid-client"),
                Map.entry("token_use", "id"),
                Map.entry("sub", "cognito-user-123"),
                Map.entry("email", "cognito-user@customer.test"),
                Map.entry("custom:role", "admin"),
                Map.entry("custom:tenant_id", "cognito-tenant"),
                Map.entry("custom:organization_name", "Cognito Customer"),
                Map.entry("exp", Instant.now().plusSeconds(300).getEpochSecond())
        ));
        Optional<AuthenticatedUser> cognitoUser = tokenAuthenticationService.validate(cognitoToken);
        assertThat(cognitoUser).isPresent();
        assertThat(cognitoUser.get().username()).isEqualTo("cognito-user@customer.test");
        assertThat(cognitoUser.get().tenantId()).isEqualTo("cognito-tenant");

        // Local email/password login must stay enabled even though Cognito is fully configured above.
        assertThat(tokenAuthenticationService.demoLoginEnabled()).isTrue();
        assertThat(tokenAuthenticationService.status().mode()).isEqualTo("hybrid");
    }

    @Test
    void remediationStepsAreRecordedOnTimelineAndNotRepeatable() throws Exception {
        String token = login();
        JsonNode incidents = getJson("/api/incidents", token);
        assertThat(incidents).hasSizeGreaterThan(0);
        long incidentId = incidents.get(0).get("id").asLong();

        JsonNode afterStep = postJson("/api/incidents/" + incidentId + "/remediation-step", token, Map.of(
                "step", "ROLLBACK_DEPLOYMENT",
                "actor", "release@sentinel.ai"
        ));
        assertThat(afterStep.get("status").asText()).isNotEqualTo("ACTIVE");
        boolean stepOnTimeline = false;
        for (JsonNode event : afterStep.get("timeline")) {
            if (event.get("label").asText().equals("Remediation step: Rollback Deployment")) {
                stepOnTimeline = true;
            }
        }
        assertThat(stepOnTimeline).isTrue();

        // Re-running the same step is rejected rather than double-recorded.
        mockMvc.perform(post("/api/incidents/" + incidentId + "/remediation-step")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "step", "ROLLBACK_DEPLOYMENT",
                                "actor", "release@sentinel.ai"
                        ))))
                .andExpect(status().isBadRequest());

        // Viewers cannot execute remediation steps.
        String viewerToken = login("viewer@sentinel.ai", "sentinel-viewer");
        mockMvc.perform(post("/api/incidents/" + incidentId + "/remediation-step")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "step", "RESTART_POD",
                                "actor", "viewer@sentinel.ai"
                        ))))
                .andExpect(status().isForbidden());
    }

    private String login() throws Exception {
        return login("release@sentinel.ai", "sentinel-release");
    }

    private String login(String username, String password) throws Exception {
        JsonNode login = postJsonWithoutAuth("/api/auth/login", Map.of(
                "username", username,
                "password", password
        ));
        JsonNode authResponse = login.get("authResponse");
        assertThat(authResponse.get("token").asText()).isNotBlank();
        return authResponse.get("token").asText();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        String response = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode getJsonWithoutAuth(String url) throws Exception {
        String response = mockMvc.perform(get(url))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        String response = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode postJsonWithoutAuth(String url, Object body) throws Exception {
        String response = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode deleteJson(String url, String token) throws Exception {
        String response = mockMvc.perform(delete(url)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode findByText(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("Could not find " + field + "=" + value);
    }

    private String githubSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String jwksJson(String kid, RSAPublicKey publicKey) throws Exception {
        return objectMapper.writeValueAsString(Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "kid", kid,
                "alg", "RS256",
                "n", base64Url(unsigned(publicKey.getModulus())),
                "e", base64Url(unsigned(publicKey.getPublicExponent()))
        ))));
    }

    private String signedRs256Jwt(KeyPair keyPair, String kid, Map<String, Object> claims) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", kid);
        String signingInput = encodeJson(header) + "." + encodeJson(claims);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature.sign());
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return base64Url(objectMapper.writeValueAsBytes(value));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
