package com.sentinelai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.security.Base32;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class MfaTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enrollConfirmThenLoginRequiresMfaCode() throws Exception {
        String email = "mfa-" + UUID.randomUUID() + "@mfaco.test";
        String token = signup("Mfa Co", email, "founder-pass-9");

        JsonNode enrollResponse = postJson("/api/account/mfa/enroll", token, Map.of(), status().isOk());
        String secret = enrollResponse.get("secret").asText();
        assertThat(enrollResponse.get("otpauthUrl").asText()).contains("otpauth://totp/");

        postJson("/api/account/mfa/confirm", token, Map.of("code", currentCode(secret)), status().isNoContent());

        JsonNode loginResult = postJsonNoAuth("/api/auth/login", Map.of(
                "username", email,
                "password", "founder-pass-9"
        ), status().isOk());
        assertThat(loginResult.get("mfaRequired").asBoolean()).isTrue();
        String challengeToken = loginResult.get("mfaChallengeToken").asText();
        assertThat(challengeToken).isNotBlank();

        JsonNode verifyResult = postJsonNoAuth("/api/auth/mfa/verify", Map.of(
                "challengeToken", challengeToken,
                "code", currentCode(secret)
        ), status().isOk());
        assertThat(verifyResult.get("token").asText()).isNotBlank();
        assertThat(verifyResult.get("username").asText()).isEqualTo(email);
    }

    @Test
    void confirmRejectsWrongCode() throws Exception {
        String email = "mfa-wrong-" + UUID.randomUUID() + "@mfaco.test";
        String token = signup("Mfa Wrong Co", email, "founder-pass-9");

        postJson("/api/account/mfa/enroll", token, Map.of(), status().isOk());

        JsonNode error = postJson("/api/account/mfa/confirm", token, Map.of("code", "000000"), status().isBadRequest());
        assertThat(error.get("message").asText()).contains("Incorrect verification code");
    }

    @Test
    void loginChallengeRejectsWrongCode() throws Exception {
        String email = "mfa-badlogin-" + UUID.randomUUID() + "@mfaco.test";
        String token = signup("Mfa Bad Login Co", email, "founder-pass-9");

        JsonNode enrollResponse = postJson("/api/account/mfa/enroll", token, Map.of(), status().isOk());
        String secret = enrollResponse.get("secret").asText();
        postJson("/api/account/mfa/confirm", token, Map.of("code", currentCode(secret)), status().isNoContent());

        JsonNode loginResult = postJsonNoAuth("/api/auth/login", Map.of(
                "username", email,
                "password", "founder-pass-9"
        ), status().isOk());
        String challengeToken = loginResult.get("mfaChallengeToken").asText();

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeToken", challengeToken,
                                "code", "000000"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginChallengeTokenIsSingleUse() throws Exception {
        String email = "mfa-singleuse-" + UUID.randomUUID() + "@mfaco.test";
        String token = signup("Mfa Single Use Co", email, "founder-pass-9");

        JsonNode enrollResponse = postJson("/api/account/mfa/enroll", token, Map.of(), status().isOk());
        String secret = enrollResponse.get("secret").asText();
        postJson("/api/account/mfa/confirm", token, Map.of("code", currentCode(secret)), status().isNoContent());

        JsonNode loginResult = postJsonNoAuth("/api/auth/login", Map.of(
                "username", email,
                "password", "founder-pass-9"
        ), status().isOk());
        String challengeToken = loginResult.get("mfaChallengeToken").asText();

        postJsonNoAuth("/api/auth/mfa/verify", Map.of(
                "challengeToken", challengeToken,
                "code", currentCode(secret)
        ), status().isOk());

        JsonNode reuseError = postJsonNoAuth("/api/auth/mfa/verify", Map.of(
                "challengeToken", challengeToken,
                "code", currentCode(secret)
        ), status().isBadRequest());
        assertThat(reuseError.get("message").asText()).contains("invalid or has expired");
    }

    @Test
    void disableMfaRequiresCorrectPassword() throws Exception {
        String email = "mfa-disable-" + UUID.randomUUID() + "@mfaco.test";
        String token = signup("Mfa Disable Co", email, "founder-pass-9");

        JsonNode enrollResponse = postJson("/api/account/mfa/enroll", token, Map.of(), status().isOk());
        String secret = enrollResponse.get("secret").asText();
        postJson("/api/account/mfa/confirm", token, Map.of("code", currentCode(secret)), status().isNoContent());

        JsonNode error = postJson("/api/account/mfa/disable", token, Map.of("password", "wrong-password"), status().isBadRequest());
        assertThat(error.get("message").asText()).contains("incorrect");

        postJson("/api/account/mfa/disable", token, Map.of("password", "founder-pass-9"), status().isNoContent());

        JsonNode loginResult = postJsonNoAuth("/api/auth/login", Map.of(
                "username", email,
                "password", "founder-pass-9"
        ), status().isOk());
        assertThat(loginResult.get("mfaRequired").asBoolean()).isFalse();
        assertThat(loginResult.get("authResponse").get("token").asText()).isNotBlank();
    }

    @Test
    void loginWithoutMfaStillWorksNormally() throws Exception {
        String email = "no-mfa-" + UUID.randomUUID() + "@mfaco.test";
        signup("No Mfa Co", email, "founder-pass-9");

        JsonNode loginResult = postJsonNoAuth("/api/auth/login", Map.of(
                "username", email,
                "password", "founder-pass-9"
        ), status().isOk());
        assertThat(loginResult.get("mfaRequired").asBoolean()).isFalse();
        assertThat(loginResult.get("authResponse").get("token").asText()).isNotBlank();
    }

    private String currentCode(String base32Secret) throws Exception {
        long timeStep = Instant.now().getEpochSecond() / 30;
        byte[] key = Base32.decode(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xF;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
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

    private JsonNode postJson(String url, String token, Object body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private JsonNode postJsonNoAuth(String url, Object body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
