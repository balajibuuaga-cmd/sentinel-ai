package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.service.ErrorTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.security.rate-limit.auth-requests-per-minute=10000"
        }
)
@AutoConfigureMockMvc
class ErrorTrackingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ErrorTrackingService errorTrackingService;

    @Test
    void recordedErrorsAppearInTenantScopedOperatorFeed() throws Exception {
        // Recording without an authenticated request context lands on the default demo
        // tenant, which is the tenant the demo admin token below reads back.
        errorTrackingService.record(
                new IllegalStateException("simulated downstream failure"),
                "/api/deployments/999/analyze",
                "POST"
        );

        String token = login("admin@sentinel.ai", "sentinel-admin");
        MvcResult result = mockMvc.perform(get("/api/operator/errors").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode errors = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(errors.isArray()).isTrue();
        assertThat(errors.size()).isGreaterThanOrEqualTo(1);
        JsonNode first = errors.get(0);
        assertThat(first.get("errorType").asText()).contains("IllegalStateException");
        assertThat(first.get("path").asText()).isEqualTo("/api/deployments/999/analyze");
        assertThat(first.get("httpMethod").asText()).isEqualTo("POST");
        assertThat(first.has("occurredAt")).isTrue();
    }

    @Test
    void errorFeedIsTenantIsolated() throws Exception {
        errorTrackingService.record(new RuntimeException("demo-tenant only"), "/api/incidents/1/status", "POST");

        String email = "errtrack-" + UUID.randomUUID() + "@errco.test";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Err Co",
                                "email", email,
                                "password", "founder-pass-9"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String freshTenantToken = objectMapper.readTree(signup.getResponse().getContentAsString()).get("token").asText();

        MvcResult result = mockMvc.perform(get("/api/operator/errors").header("Authorization", "Bearer " + freshTenantToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())).isEmpty();
    }

    @Test
    void viewersCannotReadTheErrorFeed() throws Exception {
        String viewerToken = login("viewer@sentinel.ai", "sentinel-viewer");
        mockMvc.perform(get("/api/operator/errors").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("authResponse").get("token").asText();
    }
}
