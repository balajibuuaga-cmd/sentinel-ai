package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.service.AiUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
                "sentinel.github.webhook-secret=test-webhook-secret"
        }
)
@AutoConfigureMockMvc
class AiUsageTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiUsageService aiUsageService;

    @Test
    void costEstimationUsesPublishedPerTokenPricing() {
        // Sonnet: $3/M input + $15/M output => 1M in + 1M out = $18
        assertThat(AiUsageService.estimateCostUsd("us.anthropic.claude-sonnet-4-6", 1_000_000, 1_000_000))
                .isEqualByComparingTo(new BigDecimal("18.00"));
        // 1200 in, 350 out on sonnet: 1200*3/1M + 350*15/1M = 0.0036 + 0.00525 = 0.00885
        assertThat(AiUsageService.estimateCostUsd("us.anthropic.claude-sonnet-4-6", 1200, 350))
                .isEqualByComparingTo(new BigDecimal("0.008850"));
        // Unknown models estimate to zero rather than inventing a price.
        assertThat(AiUsageService.estimateCostUsd("some-unknown-model", 1000, 1000))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordedUsageAppearsInTenantScopedSummary() throws Exception {
        // Recording without an authenticated request context lands on the default demo tenant,
        // which is the same tenant the demo admin token below reads back.
        aiUsageService.record("deployment_explanation", "us.anthropic.claude-sonnet-4-6", 1200, 350, 1800, true);
        aiUsageService.record("copilot_question", "us.anthropic.claude-sonnet-4-6", 900, 200, 1500, true);
        aiUsageService.record("executive_briefing", "us.anthropic.claude-sonnet-4-6", 0, 0, 2500, false);

        String token = login("admin@sentinel.ai", "sentinel-admin");
        MvcResult result = mockMvc.perform(get("/api/ai/usage").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summary = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(summary.get("totalCalls").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(summary.get("failedCalls").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("totalInputTokens").asLong()).isGreaterThanOrEqualTo(2100);
        assertThat(summary.get("totalOutputTokens").asLong()).isGreaterThanOrEqualTo(550);
        assertThat(new BigDecimal(summary.get("estimatedCostUsd").asText()))
                .isGreaterThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(summary.get("averageLatencyMs").asLong()).isGreaterThan(0);
        assertThat(summary.get("byOperation").isArray()).isTrue();
        assertThat(summary.get("byOperation").size()).isGreaterThanOrEqualTo(3);
        assertThat(summary.get("recentCalls").isArray()).isTrue();
        JsonNode firstRecent = summary.get("recentCalls").get(0);
        assertThat(firstRecent.has("operation")).isTrue();
        assertThat(firstRecent.has("estimatedCostUsd")).isTrue();
        assertThat(firstRecent.has("succeeded")).isTrue();
    }

    @Test
    void usageSummaryIsTenantIsolated() throws Exception {
        aiUsageService.record("deployment_explanation", "us.anthropic.claude-sonnet-4-6", 5000, 400, 2000, true);

        String email = "aiusage-" + UUID.randomUUID() + "@usageco.test";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Usage Co",
                                "email", email,
                                "password", "founder-pass-9"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String freshTenantToken = objectMapper.readTree(signup.getResponse().getContentAsString()).get("token").asText();

        MvcResult result = mockMvc.perform(get("/api/ai/usage").header("Authorization", "Bearer " + freshTenantToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summary = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(summary.get("totalCalls").asLong()).isZero();
        assertThat(new BigDecimal(summary.get("estimatedCostUsd").asText())).isEqualByComparingTo(BigDecimal.ZERO);
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
