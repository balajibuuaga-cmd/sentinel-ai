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
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret"
        }
)
@AutoConfigureMockMvc
@Import(EmailTestConfig.class)
class TeamManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailService emailService;

    @Test
    void adminCanInviteListAndManageTeamMembers() throws Exception {
        String adminEmail = "founder-" + UUID.randomUUID() + "@teamco.test";
        String adminToken = signupAndLogin("Team Co", adminEmail, "founder-pass-9");

        String inviteeEmail = "teammate-" + UUID.randomUUID() + "@teamco.test";
        JsonNode invited = postJson("/api/team/invite", adminToken, Map.of(
                "email", inviteeEmail,
                "role", "RELEASE_MANAGER"
        ), status().isCreated());
        assertThat(invited.get("email").asText()).isEqualTo(inviteeEmail);
        assertThat(invited.get("role").asText()).isEqualTo("RELEASE_MANAGER");
        assertThat(invited.get("you").asBoolean()).isFalse();
        long inviteeId = invited.get("id").asLong();

        JsonNode roster = getJson("/api/team/members", adminToken);
        assertThat(roster).hasSize(2);

        CapturingEmailService capturing = (CapturingEmailService) emailService;
        CapturingEmailService.SentEmail sentEmail = capturing.sent.stream()
                .filter(candidate -> candidate.to().equals(inviteeEmail))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No invite email captured for " + inviteeEmail));
        Matcher matcher = Pattern.compile("token=(\\S+)").matcher(sentEmail.body());
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "newPassword", "teammate-set-pass-9"
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", inviteeEmail,
                                "password", "teammate-set-pass-9"
                        ))))
                .andExpect(status().isOk());

        JsonNode roleUpdated = putJson("/api/team/members/" + inviteeId + "/role", adminToken, Map.of("role", "VIEWER"), status().isOk());
        assertThat(roleUpdated.get("role").asText()).isEqualTo("VIEWER");

        mockMvc.perform(delete("/api/team/members/" + inviteeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        JsonNode rosterAfterRemoval = getJson("/api/team/members", adminToken);
        assertThat(rosterAfterRemoval).hasSize(1);
    }

    @Test
    void nonAdminCannotInviteTeamMembers() throws Exception {
        String adminEmail = "founder-" + UUID.randomUUID() + "@teamco.test";
        String adminToken = signupAndLogin("Restricted Co", adminEmail, "founder-pass-9");

        String viewerEmail = "viewer-" + UUID.randomUUID() + "@teamco.test";
        postJson("/api/team/invite", adminToken, Map.of("email", viewerEmail, "role", "VIEWER"), status().isCreated());

        CapturingEmailService capturing = (CapturingEmailService) emailService;
        CapturingEmailService.SentEmail sentEmail = capturing.sent.stream()
                .filter(candidate -> candidate.to().equals(viewerEmail))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No invite email captured for " + viewerEmail));
        String rawToken = extractToken(sentEmail.body());
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "newPassword", "viewer-set-pass-9"))))
                .andExpect(status().isNoContent());

        String viewerToken = login(viewerEmail, "viewer-set-pass-9");

        mockMvc.perform(post("/api/team/invite")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "another-" + UUID.randomUUID() + "@teamco.test", "role", "VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotChangeOwnRoleOrRemoveSelf() throws Exception {
        String adminEmail = "solo-" + UUID.randomUUID() + "@teamco.test";
        String adminToken = signupAndLogin("Solo Co", adminEmail, "solo-pass-9");

        JsonNode roster = getJson("/api/team/members", adminToken);
        long selfId = roster.get(0).get("id").asLong();

        JsonNode roleError = putJson("/api/team/members/" + selfId + "/role", adminToken, Map.of("role", "VIEWER"), status().isBadRequest());
        assertThat(roleError.get("message").asText()).contains("cannot change your own role");

        mockMvc.perform(delete("/api/team/members/" + selfId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteRejectsDuplicateEmailAndInvalidRole() throws Exception {
        String adminEmail = "dupinvite-" + UUID.randomUUID() + "@teamco.test";
        String adminToken = signupAndLogin("Dup Invite Co", adminEmail, "founder-pass-9");

        JsonNode duplicateError = postJson("/api/team/invite", adminToken, Map.of("email", adminEmail, "role", "VIEWER"), status().isBadRequest());
        assertThat(duplicateError.get("message").asText()).contains("already exists");

        JsonNode roleError = postJson("/api/team/invite", adminToken, Map.of(
                "email", "bad-role-" + UUID.randomUUID() + "@teamco.test",
                "role", "SUPER_ADMIN"
        ), status().isBadRequest());
        assertThat(roleError.get("message").asText()).contains("Role must be one of");
    }

    @Test
    void teamRosterIsTenantIsolated() throws Exception {
        String tenantAAdmin = "tenanta-" + UUID.randomUUID() + "@teamco.test";
        String tenantAToken = signupAndLogin("Tenant A Co", tenantAAdmin, "tenant-a-pass-9");

        String tenantBAdmin = "tenantb-" + UUID.randomUUID() + "@teamco.test";
        signupAndLogin("Tenant B Co", tenantBAdmin, "tenant-b-pass-9");

        JsonNode tenantARoster = getJson("/api/team/members", tenantAToken);
        assertThat(tenantARoster).hasSize(1);
        assertThat(tenantARoster.get(0).get("email").asText()).isEqualTo(tenantAAdmin);
    }

    private String extractToken(String emailBody) {
        Matcher matcher = Pattern.compile("token=(\\S+)").matcher(emailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String signupAndLogin(String organizationName, String email, String password) throws Exception {
        JsonNode response = postJsonWithoutAuth("/api/auth/signup", Map.of(
                "organizationName", organizationName,
                "email", email,
                "password", password
        ), status().isCreated());
        return response.get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        JsonNode response = postJsonWithoutAuth("/api/auth/login", Map.of(
                "username", email,
                "password", password
        ), status().isOk());
        return response.get("token").asText();
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

    private JsonNode putJson(String url, String token, Object body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(put(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJsonWithoutAuth(String url, Object body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
