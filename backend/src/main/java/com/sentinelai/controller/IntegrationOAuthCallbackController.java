package com.sentinelai.controller;

import com.sentinelai.model.IntegrationProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class IntegrationOAuthCallbackController {

    @GetMapping("/integrations/{provider}/callback")
    public RedirectView callback(
            @PathVariable IntegrationProvider provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        StringBuilder redirect = new StringBuilder("/?integrationProvider=").append(provider.name());
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
