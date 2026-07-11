package com.sentinelai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                "sentinel.security.rate-limit.auth-requests-per-minute=5",
                "sentinel.security.rate-limit.requests-per-minute=120"
        }
)
@AutoConfigureMockMvc
class AuthRateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authEndpointsAreThrottledTighterThanGeneralTraffic() throws Exception {
        // A distinct source IP so this test's counter never collides with other tests.
        String ip = "203.0.113." + (int) (System.nanoTime() % 200 + 1);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "ratelimit-" + UUID.randomUUID() + "@nope.test",
                "password", "wrong-password"
        ));

        // Auth budget is 5/min: the first 5 are processed (401 for bad creds),
        // the 6th is rejected by the rate limiter with 429.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("5"));
    }

    @Test
    void generalTrafficUsesTheLooserBudgetInItsOwnBucket() throws Exception {
        // From the same IP, general GET traffic is counted in a separate bucket,
        // so it is not throttled by the auth budget - well within 120/min here.
        String ip = "203.0.113." + (int) (System.nanoTime() % 55 + 1);
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(get("/api/auth/status").header("X-Forwarded-For", ip))
                    .andExpect(status().isOk())
                    .andExpect(result ->
                            assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("120"));
        }
    }
}
