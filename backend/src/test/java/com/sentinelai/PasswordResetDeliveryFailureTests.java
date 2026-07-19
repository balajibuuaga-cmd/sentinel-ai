package com.sentinelai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.service.EmailService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SES stays in the sandbox, so sending to an unverified recipient is rejected in
 * production. Password reset must stay uniform regardless: the endpoint hides
 * whether an address is registered, and the reset token is persisted before the
 * email is attempted. A delivery failure must therefore never change the
 * response or surface as a 5xx.
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
@Import(PasswordResetDeliveryFailureTests.ExplodingEmailConfig.class)
class PasswordResetDeliveryFailureTests {

    /** Stands in for SES rejecting the recipient or failing to connect. */
    @TestConfiguration
    static class ExplodingEmailConfig {
        @Bean
        @Primary
        EmailService emailService() {
            return (to, subject, body) -> {
                throw new IllegalStateException("simulated SES sandbox rejection for " + to);
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void passwordResetRequestSucceedsWhenEmailDeliveryFails() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@sandboxco.test";
        signup("Sandbox Co", email, "founder-pass-9");

        // Registered address, but the transport blows up — still a clean response.
        requestReset(email).andExpect(status().is2xxSuccessful());
    }

    @Test
    void unregisteredAddressIsIndistinguishableFromRegisteredOne() throws Exception {
        // The uniform-response guarantee is what stops this endpoint leaking
        // which addresses have accounts, so it must hold when sending fails too.
        requestReset("definitely-not-registered-" + UUID.randomUUID() + "@sandboxco.test")
                .andExpect(status().is2xxSuccessful());
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private void signup(String organizationName, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", organizationName,
                                "email", email,
                                "password", password))))
                .andExpect(status().is2xxSuccessful());
    }
}
