package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
@Import(EmailTestConfig.class)
class AuthSignupTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailService emailService;

    @Test
    void signupCreatesAccountAndAutoLogsIn() throws Exception {
        String email = "founder-" + UUID.randomUUID() + "@newco.test";
        JsonNode response = postJson("/api/auth/signup", Map.of(
                "organizationName", "New Co",
                "email", email,
                "password", "correct-horse-9"
        ), status().isCreated());

        assertThat(response.get("token").asText()).isNotBlank();
        assertThat(response.get("username").asText()).isEqualTo(email);
        assertThat(response.get("role").asText()).isEqualTo("ADMIN");
        assertThat(response.get("organizationName").asText()).isEqualTo("New Co");
        assertThat(response.get("tenantId").asText()).startsWith("new-co-");
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@newco.test";
        postJson("/api/auth/signup", Map.of(
                "organizationName", "Dup Co",
                "email", email,
                "password", "correct-horse-9"
        ), status().isCreated());

        JsonNode error = postJson("/api/auth/signup", Map.of(
                "organizationName", "Dup Co Again",
                "email", email,
                "password", "another-pass-9"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("already exists");
    }

    @Test
    void signupRejectsWeakPassword() throws Exception {
        JsonNode error = postJson("/api/auth/signup", Map.of(
                "organizationName", "Weak Co",
                "email", "weak-" + UUID.randomUUID() + "@newco.test",
                "password", "short1"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("at least 10 characters");
    }

    @Test
    void signupRejectsPasswordWithoutDigit() throws Exception {
        JsonNode error = postJson("/api/auth/signup", Map.of(
                "organizationName", "Weak Co",
                "email", "weak2-" + UUID.randomUUID() + "@newco.test",
                "password", "allletters"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("one letter and one digit");
    }

    @Test
    void repeatedFailedLoginsLockTheAccount() throws Exception {
        String email = "lockout-" + UUID.randomUUID() + "@newco.test";
        postJson("/api/auth/signup", Map.of(
                "organizationName", "Lockout Co",
                "email", email,
                "password", "correct-horse-9"
        ), status().isCreated());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", email,
                                    "password", "wrong-password"
                            ))))
                    .andExpect(status().isUnauthorized());
        }

        JsonNode lockedError = postJson("/api/auth/login", Map.of(
                "username", email,
                "password", "correct-horse-9"
        ), status().isBadRequest());

        assertThat(lockedError.get("message").asText()).contains("temporarily locked");
    }

    @Test
    void passwordResetRequestSendsEmailAndConfirmChangesPassword() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@newco.test";
        postJson("/api/auth/signup", Map.of(
                "organizationName", "Reset Co",
                "email", email,
                "password", "original-pass-9"
        ), status().isCreated());

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isNoContent());

        CapturingEmailService capturing = (CapturingEmailService) emailService;
        CapturingEmailService.SentEmail sentEmail = capturing.sent.stream()
                .filter(candidate -> candidate.to().equals(email))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No reset email captured for " + email));
        Matcher matcher = Pattern.compile("token=(\\S+)").matcher(sentEmail.body());
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "newPassword", "brand-new-pass-9"
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", email,
                                "password", "brand-new-pass-9"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRequestForUnknownEmailReturnsNoContentWithoutSendingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody-" + UUID.randomUUID() + "@newco.test"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordResetConfirmRejectsInvalidToken() throws Exception {
        JsonNode error = postJson("/api/auth/password-reset/confirm", Map.of(
                "token", "not-a-real-token",
                "newPassword", "brand-new-pass-9"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("invalid or has expired");
    }

    private JsonNode postJson(String url, Object body, org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
