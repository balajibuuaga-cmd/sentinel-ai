package com.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The endpoint verified signatures correctly and then deserialised the body
 * straight into GitHubWebhookRequest, whose fields GitHub does not send: its
 * repository is an object rather than a string, and nothing in the payload is
 * called serviceName, ownerTeam or commitSha. Every delivery from a real
 * repository failed, so the feature only worked for a caller hand-rolling
 * Sentinel's own shape.
 *
 * <p>These payloads are trimmed to the fields the translator reads, in the
 * structure GitHub actually sends.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.security.rate-limit.auth-requests-per-minute=10000"
        }
)
@AutoConfigureMockMvc
class GitHubNativeWebhookTests {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aRealPullRequestEventCreatesADeployment() throws Exception {
        String body = """
                {
                  "action": "opened",
                  "repository": {
                    "full_name": "balajibuuaga-cmd/sentinel-ai",
                    "default_branch": "main",
                    "owner": {"login": "balajibuuaga-cmd"}
                  },
                  "pull_request": {
                    "title": "Correct the test counts in the README",
                    "head": {"sha": "abc123def456"}
                  },
                  "sender": {"login": "balajibuuaga-cmd"}
                }
                """;

        JsonNode deployment = postEvent("pull_request", body, status().isOk());

        // Deployment records the service, not the raw repository string: the
        // translator derives sentinel-ai from balajibuuaga-cmd/sentinel-ai.
        assertThat(deployment.get("serviceName").asText()).isEqualTo("sentinel-ai");
        assertThat(deployment.get("ownerTeam").asText()).isEqualTo("balajibuuaga-cmd");
        assertThat(deployment.get("pullRequestTitle").asText())
                .isEqualTo("Correct the test counts in the README");
        assertThat(deployment.get("commitSha").asText()).isEqualTo("abc123def456");
        // A pull request is a candidate for production, not production itself.
        assertThat(deployment.get("environment").asText()).isEqualTo("review");
    }

    @Test
    void aPushToTheDefaultBranchIsTreatedAsProduction() throws Exception {
        String body = """
                {
                  "ref": "refs/heads/main",
                  "repository": {
                    "full_name": "balajibuuaga-cmd/sentinel-ai",
                    "default_branch": "main",
                    "owner": {"login": "balajibuuaga-cmd"}
                  },
                  "head_commit": {
                    "id": "feed0000beef",
                    "message": "Ship the webhook translator\\n\\nlonger body text",
                    "modified": ["backend/src/main/java/Thing.java"]
                  },
                  "pusher": {"name": "balajibuuaga-cmd"}
                }
                """;

        JsonNode deployment = postEvent("push", body, status().isOk());

        assertThat(deployment.get("environment").asText()).isEqualTo("production");
        assertThat(deployment.get("commitSha").asText()).isEqualTo("feed0000beef");
        // Only the subject line, not the whole commit body.
        assertThat(deployment.get("pullRequestTitle").asText()).isEqualTo("Ship the webhook translator");
    }

    @Test
    void aPushToAFeatureBranchIsNotProduction() throws Exception {
        String body = """
                {
                  "ref": "refs/heads/docs/correct-test-counts",
                  "repository": {
                    "full_name": "balajibuuaga-cmd/sentinel-ai",
                    "default_branch": "main",
                    "owner": {"login": "balajibuuaga-cmd"}
                  },
                  "head_commit": {"id": "cafe1234", "message": "wip"},
                  "pusher": {"name": "balajibuuaga-cmd"}
                }
                """;

        JsonNode deployment = postEvent("push", body, status().isOk());

        assertThat(deployment.get("environment").asText()).isEqualTo("review");
    }

    @Test
    void theInitialPingIsAcceptedRatherThanRejected() throws Exception {
        // GitHub sends this the moment a webhook is created. Answering with an
        // error marks the delivery failed and, after enough failures, disables
        // the webhook.
        postEvent("ping", "{\"zen\":\"Design for failure.\",\"hook_id\":1}", status().isNoContent());
    }

    @Test
    void aClosedPullRequestCarriesNoDeploymentSignal() throws Exception {
        String body = """
                {
                  "action": "closed",
                  "repository": {"full_name": "o/r", "default_branch": "main", "owner": {"login": "o"}},
                  "pull_request": {"title": "done", "head": {"sha": "abc"}},
                  "sender": {"login": "o"}
                }
                """;

        postEvent("pull_request", body, status().isNoContent());
    }

    @Test
    void aFullSizeGitHubPayloadIsStored() throws Exception {
        // The trimmed fixtures above are why the column length was missed: it was
        // varchar(6000), sized against Sentinel's own hand-rolled shape, while
        // GitHub's real pull_request event carries the whole repository, pull
        // request and sender objects. Deliveries failed on insert with SQLSTATE
        // 22001 before the event was processed at all.
        //
        // Note what this test does NOT do. Removing the widening migration and
        // restoring length=6000 leaves it passing, because H2 does not enforce
        // varchar length the way PostgreSQL does. It exercises the full-size path
        // and guards the translator against large bodies, but the column
        // constraint itself is only provable against PostgreSQL. The evidence for
        // that fix is the production log and a real GitHub delivery, not this.
        String filler = "x".repeat(20_000);
        String body = """
                {
                  "action": "opened",
                  "repository": {
                    "full_name": "balajibuuaga-cmd/sentinel-ai",
                    "default_branch": "main",
                    "owner": {"login": "balajibuuaga-cmd"},
                    "description": "%s"
                  },
                  "pull_request": {"title": "Big payload", "head": {"sha": "deadbeef"}},
                  "sender": {"login": "balajibuuaga-cmd"}
                }
                """.formatted(filler);
        assertThat(body.length()).isGreaterThan(6000);

        JsonNode deployment = postEvent("pull_request", body, status().isOk());

        assertThat(deployment.get("commitSha").asText()).isEqualTo("deadbeef");
    }

    @Test
    void anUnsignedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode postEvent(String eventType, String body, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/webhooks/github")
                        .header("X-Hub-Signature-256", sign(body))
                        .header("X-GitHub-Event", eventType)
                        .header("X-GitHub-Delivery", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expected)
                .andReturn();
        String response = result.getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
