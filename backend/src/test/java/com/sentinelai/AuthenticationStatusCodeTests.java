package com.sentinelai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Clients must be able to tell "your session expired, sign in again" from
 * "you are signed in but this is not for your role". Without a configured
 * AuthenticationEntryPoint, Spring Security answers both with 403, and the
 * console rendered a "backend unreachable" error screen on session expiry
 * instead of redirecting to login.
 *
 * <p>Contract: 401 = unauthenticated, 403 = authenticated but forbidden.
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
class AuthenticationStatusCodeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/operator/console"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/deployments").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedButInsufficientRoleIsForbiddenNotUnauthorized() throws Exception {
        // A VIEWER is authenticated, so hitting an ADMIN/RELEASE_MANAGER route
        // must stay 403 — turning this into 401 would log the user straight out.
        String viewerToken = login("viewer@sentinel.ai", "sentinel-viewer");

        mockMvc.perform(get("/api/operator/console").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanReachPermittedRoutes() throws Exception {
        String viewerToken = login("viewer@sentinel.ai", "sentinel-viewer");

        mockMvc.perform(get("/api/deployments").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        // Login wraps the session in `authResponse` so it can also carry an MFA challenge.
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("authResponse").get("token").asText();
    }
}
