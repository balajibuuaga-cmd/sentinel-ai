package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

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
class AccountSettingsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void currentAccountReturnsOwnProfile() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@acctco.test";
        String token = signup("Acct Co", email, "founder-pass-9");

        JsonNode profile = getJson("/api/account/me", token);
        assertThat(profile.get("email").asText()).isEqualTo(email);
        assertThat(profile.get("role").asText()).isEqualTo("ADMIN");
        assertThat(profile.get("organizationName").asText()).isEqualTo("Acct Co");
    }

    @Test
    void changePasswordWithCorrectCurrentPasswordThenLoginWithNewPassword() throws Exception {
        String email = "changepw-" + UUID.randomUUID() + "@acctco.test";
        String token = signup("Change Pw Co", email, "original-pass-9");

        mockMvc.perform(post("/api/account/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "original-pass-9",
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

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", email,
                                "password", "original-pass-9"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        String email = "wrongpw-" + UUID.randomUUID() + "@acctco.test";
        String token = signup("Wrong Pw Co", email, "original-pass-9");

        JsonNode error = postJson("/api/account/change-password", token, Map.of(
                "currentPassword", "not-the-real-password",
                "newPassword", "brand-new-pass-9"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("incorrect");
    }

    @Test
    void changePasswordRejectsWeakNewPassword() throws Exception {
        String email = "weakpw-" + UUID.randomUUID() + "@acctco.test";
        String token = signup("Weak Pw Co", email, "original-pass-9");

        JsonNode error = postJson("/api/account/change-password", token, Map.of(
                "currentPassword", "original-pass-9",
                "newPassword", "short1"
        ), status().isBadRequest());

        assertThat(error.get("message").asText()).contains("at least 10 characters");
    }

    private String signup(String organizationName, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", organizationName,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String url, String token, Object body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
