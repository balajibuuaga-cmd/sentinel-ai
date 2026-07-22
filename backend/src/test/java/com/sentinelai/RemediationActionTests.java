package com.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * A remediation step performs its external action when a real integration backs
 * it, and records honestly when one does not. No Slack webhook is configured in
 * this suite, so Notify Slack records rather than sends; Rollback Deployment is a
 * real, testable effect because it moves the linked deployment's own record to
 * ROLLED_BACK.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.security.rate-limit.auth-requests-per-minute=10000"
        }
)
@AutoConfigureMockMvc
class RemediationActionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void notifySlackRecordsWhenNoWebhookIsConfigured() throws Exception {
        String token = login();
        JsonNode incident = firstActiveIncident(token);
        long id = incident.get("id").asLong();

        JsonNode updated = runStep(token, id, "NOTIFY_SLACK");
        String detail = latestRemediationDetail(updated, "Notify Slack");

        assertThat(detail).containsIgnoringCase("no slack webhook");
    }

    @Test
    void restartPodRecordsThatNoOrchestratorIsConnected() throws Exception {
        String token = login();
        long id = firstActiveIncident(token).get("id").asLong();

        JsonNode updated = runStep(token, id, "RESTART_POD");
        String detail = latestRemediationDetail(updated, "Restart Pod");

        assertThat(detail).containsIgnoringCase("no container orchestrator");
    }

    @Test
    void rollbackDeploymentMovesTheLinkedDeploymentToRolledBack() throws Exception {
        String token = login();
        JsonNode incident = firstActiveIncident(token);
        long incidentId = incident.get("id").asLong();
        long deploymentId = incident.get("deploymentId").asLong();

        JsonNode updated = runStep(token, incidentId, "ROLLBACK_DEPLOYMENT");
        String detail = latestRemediationDetail(updated, "Rollback Deployment");
        assertThat(detail).containsIgnoringCase("rolled back");

        JsonNode deployment = getJson(token, "/api/deployments/" + deploymentId);
        assertThat(deployment.get("status").asText()).isEqualTo("ROLLED_BACK");
    }

    private JsonNode firstActiveIncident(String token) throws Exception {
        JsonNode incidents = getJson(token, "/api/incidents");
        assertThat(incidents).isNotEmpty();
        // Choose one that is linked to a deployment, which the rollback needs.
        for (JsonNode incident : incidents) {
            if (!incident.get("deploymentId").isNull()) {
                return incident;
            }
        }
        return incidents.get(0);
    }

    private JsonNode runStep(String token, long id, String step) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/incidents/" + id + "/remediation-step")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("step", step, "actor", "admin@sentinel.ai"))))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String latestRemediationDetail(JsonNode incident, String label) {
        String want = "Remediation step: " + label;
        String detail = null;
        for (JsonNode event : incident.get("timeline")) {
            if (event.get("label").asText().equals(want)) {
                detail = event.get("detail").asText();
            }
        }
        assertThat(detail).as("timeline entry for " + label).isNotNull();
        return detail;
    }

    private JsonNode getJson(String token, String url) throws Exception {
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
}
