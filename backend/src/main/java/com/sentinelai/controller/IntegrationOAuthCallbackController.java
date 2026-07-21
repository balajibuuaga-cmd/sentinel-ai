package com.sentinelai.controller;

import com.sentinelai.model.IntegrationProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class IntegrationOAuthCallbackController {

    /**
     * Providers redirect here with the authorization code.
     *
     * <p>The provider arrives as a String rather than the enum because the
     * configured redirect URIs spell it in lower case, and Spring's enum binding
     * is case-sensitive: binding straight to {@link IntegrationProvider} answered
     * GitHub's redirect with 400 and left the user on a blank page. An unknown
     * provider redirects to the console with an error instead of failing the
     * request, since the user has just come back from an external site and needs
     * to land somewhere.
     */
    @GetMapping("/integrations/{provider}/callback")
    public RedirectView callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        IntegrationProvider resolved;
        try {
            resolved = IntegrationProvider.valueOf(provider.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return new RedirectView("/?integrationError=" + url("unknown_provider"));
        }

        StringBuilder redirect = new StringBuilder("/?integrationProvider=").append(resolved.name());
        if (code != null && !code.isBlank()) {
            redirect.append("&code=").append(url(code));
        }
        if (state != null && !state.isBlank()) {
            redirect.append("&state=").append(url(state));
        }
        if (error != null && !error.isBlank()) {
            redirect.append("&integrationError=").append(url(error));
        }
        return new RedirectView(redirect.toString());
    }

    private String url(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
