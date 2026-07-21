package com.sentinelai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Providers redirect back to the URI configured on their side, and those URIs
 * spell the provider in lower case. Binding that path segment straight to the
 * IntegrationProvider enum answered GitHub's redirect with 400, so authorizing
 * the app left the user on a blank page with the code discarded.
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
class IntegrationOAuthCallbackTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aLowercaseProviderInTheCallbackPathIsAccepted() throws Exception {
        mockMvc.perform(get("/integrations/github/callback").param("code", "abc123").param("state", "sentinel-demo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("integrationProvider=GITHUB")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code=abc123")));
    }

    @Test
    void anUppercaseProviderStillWorks() throws Exception {
        mockMvc.perform(get("/integrations/JIRA/callback").param("code", "xyz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("integrationProvider=JIRA")));
    }

    @Test
    void aProviderErrorIsPassedBackToTheConsole() throws Exception {
        mockMvc.perform(get("/integrations/github/callback").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("integrationError=access_denied")));
    }

    @Test
    void anUnknownProviderRedirectsRatherThanFailingTheRequest() throws Exception {
        // The user has just returned from an external site and must land somewhere.
        mockMvc.perform(get("/integrations/not-a-provider/callback").param("code", "abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("integrationError=unknown_provider")));
    }
}
