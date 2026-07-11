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

import java.util.Map;

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
class SecretShieldTests {

    // Fake-but-well-formed values for exercising the pattern set. Not real credentials.
    private static final String FAKE_AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String FAKE_GITHUB_TOKEN = "ghp_" + "abcdefghijklmnopqrstuvwxyz0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void scannerBlocksKnownSecretShapesAndMasksValues() throws Exception {
        String token = login();
        String content = String.join("\n",
                "public class Config {",
                "    static final String AWS_KEY = \"" + FAKE_AWS_KEY + "\";",
                "    static final String GH = \"" + FAKE_GITHUB_TOKEN + "\";",
                "    static final String DB = \"postgres://app:s3cr3tPassw0rd@db.internal:5432/app\";",
                "}"
        );

        JsonNode result = scan(token, content, "Config.java");

        assertThat(result.get("blockedCount").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(result.get("wouldBlockCommit").asBoolean()).isTrue();

        // The raw secret values must never appear anywhere in the response.
        String raw = result.toString();
        assertThat(raw).doesNotContain(FAKE_AWS_KEY);
        assertThat(raw).doesNotContain(FAKE_GITHUB_TOKEN);
        assertThat(raw).doesNotContain("s3cr3tPassw0rd");
        assertThat(raw).contains("****");
    }

    @Test
    void withoutAiGateScannerHitsStayBlockedConservatively() throws Exception {
        // Tests run with the deterministic provider, so no AI downgrade exists:
        // even a placeholder-looking hit must stay blocked rather than cleared.
        String token = login();
        JsonNode result = scan(token, "password = \"hunter2hunter2A\"", ".env");

        assertThat(result.get("aiGateAvailable").asBoolean()).isFalse();
        for (JsonNode finding : result.get("findings")) {
            assertThat(finding.get("verdict").asText()).isNotEqualTo("CLEARED");
        }
    }

    @Test
    void cleanContentProducesNoFindings() throws Exception {
        String token = login();
        String content = String.join("\n",
                "export function add(a: number, b: number) {",
                "  return a + b;",
                "}",
                "const label = 'hello world';"
        );

        JsonNode result = scan(token, content, "math.ts");

        assertThat(result.get("findings")).isEmpty();
        assertThat(result.get("wouldBlockCommit").asBoolean()).isFalse();
        assertThat(result.get("linesScanned").asInt()).isEqualTo(4);
    }

    @Test
    void riskGateFallbackWarnsOnModerateEntropyAssignmentBelowScannerThreshold() throws Exception {
        // A credential keyword with a value whose entropy (3.0 bits/char) sits below
        // the scanner's 3.6 firing threshold, so the scanner misses it. With no AI
        // available the risk-gate entropy fallback should still warn on it.
        String token = login();
        JsonNode result = scan(token, "api_key: aabbccddeeffgghh", "settings.yaml");

        boolean warned = false;
        for (JsonNode finding : result.get("findings")) {
            if (finding.get("verdict").asText().equals("WARN")) {
                warned = true;
                assertThat(finding.get("source").asText()).isEqualTo("RISK_GATE");
            }
        }
        assertThat(warned).isTrue();
    }

    private JsonNode scan(String token, String content, String filename) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/security/secret-scan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", content,
                                "filename", filename
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin@sentinel.ai",
                                "password", "sentinel-admin"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("authResponse").get("token").asText();
    }
}
