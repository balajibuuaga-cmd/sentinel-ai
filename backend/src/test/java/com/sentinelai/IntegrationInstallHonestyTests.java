package com.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Without provider credentials there is no OAuth exchange and no stored token, so
 * the integration cannot reach GitHub or Jira at all. Installing one must say so.
 *
 * <p>It previously reported CONNECTED with a SUCCESS sync, health 100, and the
 * detail "Initial OAuth installation and bootstrap sync completed." — alongside
 * invented record and latency figures. That presents an integration that has
 * never contacted the provider as a working one, which is worse than an obviously
 * broken control because nothing about it looks wrong.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.security.rate-limit.auth-requests-per-minute=10000",
                // No provider credentials configured: the demo path.
                "sentinel.integrations.real-exchange-enabled=false"
        }
)
@AutoConfigureMockMvc
class IntegrationInstallHonestyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void demoInstallDoesNotClaimAnOAuthExchange() throws Exception {
        String token = login();

        JsonNode connection = install(token, "GITHUB");

        // Assert on what a user actually reads. tokenSecretRef is not a usable
        // signal here: seeded connections carry a placeholder value that looks
        // like a stored token whether or not one exists.
        assertThat(connection.get("statusDetail").asText())
                .doesNotContain("Initial OAuth installation")
                .containsIgnoringCase("demo");
    }

    @Test
    void demoInstallDoesNotRecordAHealthySuccessfulSync() throws Exception {
        String token = login();
        install(token, "JIRA");

        JsonNode history = getJson("/api/integration-connections/sync-history", token);
        JsonNode latest = history.get(0);

        // A sync that inspected nothing must not look green in the history.
        assertThat(latest.get("status").asText()).isNotEqualTo("SUCCESS");
        assertThat(latest.get("recordsInspected").asInt()).isZero();
    }

    private JsonNode install(String token, String provider) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/integration-connections/" + provider + "/install")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("externalAccount", "acme/checkout-service"))))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin@sentinel.ai", "password", "sentinel-admin"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("authResponse").get("token").asText();
    }

    @Test
    void aGitHubAccountMustBeOwnerSlashRepository() throws Exception {
        String token = login();
        JsonNode connection = install(token, "GITHUB");
        long id = connection.get("id").asLong();

        // A bare scope list or username is not a repository. This rejects the
        // value that an earlier bug wrote here: GitHub's token response carries
        // no account_id, so the OAuth scope string won the fallback chain and
        // the connection was named "read:org,read:user,repo".
        mockMvc.perform(put("/api/integration-connections/" + id + "/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("externalAccount", "read:org,read:user,repo"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aValidRepositoryIsAcceptedWithoutTouchingTheConnectionState() throws Exception {
        String token = login();
        JsonNode connection = install(token, "GITHUB");
        long id = connection.get("id").asLong();

        MvcResult result = mockMvc.perform(put("/api/integration-connections/" + id + "/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("externalAccount", "octocat/hello-world"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updated = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(updated.get("externalAccount").asText()).isEqualTo("octocat/hello-world");
        assertThat(updated.get("status").asText()).isEqualTo("CONNECTED");
    }

    @Test
    void syncingWithoutALiveConnectionReportsNothingFetched() throws Exception {
        String token = login();
        JsonNode connection = install(token, "CI");
        long id = connection.get("id").asLong();

        MvcResult result = mockMvc.perform(post("/api/integration-connections/" + id + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode synced = objectMapper.readTree(result.getResponse().getContentAsString());
        // This used to hash tenant and provider into a status and record count and
        // announce "Latest sync completed successfully".
        assertThat(synced.get("statusDetail").asText())
                .doesNotContain("Latest sync completed successfully")
                .containsIgnoringCase("nothing was fetched");
        assertThat(synced.get("healthScore").asInt()).isZero();

        JsonNode history = getJson("/api/integration-connections/sync-history", token);
        JsonNode latest = history.get(0);
        assertThat(latest.get("status").asText()).isNotEqualTo("SUCCESS");
        assertThat(latest.get("recordsInspected").asInt()).isZero();
        assertThat(latest.get("latencyMs").asInt()).isZero();
    }
}
