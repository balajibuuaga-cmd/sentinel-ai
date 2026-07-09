package com.sentinelai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class TokenAuthenticationService {

    private final JwtService jwtService;
    private final CognitoJwtValidator cognitoJwtValidator;
    private final String mode;
    private final String clientId;
    private final String hostedUiBaseUrl;
    private final String redirectUri;
    private final String logoutUri;

    public TokenAuthenticationService(
            JwtService jwtService,
            CognitoJwtValidator cognitoJwtValidator,
            @Value("${sentinel.auth.mode:demo}") String mode,
            @Value("${sentinel.auth.cognito.client-id:}") String clientId,
            @Value("${sentinel.auth.cognito.hosted-ui-base-url:}") String hostedUiBaseUrl,
            @Value("${sentinel.auth.cognito.redirect-uri:}") String redirectUri,
            @Value("${sentinel.auth.cognito.logout-uri:}") String logoutUri
    ) {
        this.jwtService = jwtService;
        this.cognitoJwtValidator = cognitoJwtValidator;
        this.mode = mode;
        this.clientId = clientId;
        this.hostedUiBaseUrl = trimTrailingSlash(hostedUiBaseUrl);
        this.redirectUri = redirectUri;
        this.logoutUri = logoutUri;
    }

    public Optional<AuthenticatedUser> validate(String token) {
        if ("cognito".equalsIgnoreCase(mode)) {
            return cognitoJwtValidator.validate(token);
        }
        return jwtService.validate(token).map(user -> new AuthenticatedUser(
                user.username(),
                user.role(),
                user.tenantId(),
                user.organizationName()
        ));
    }

    public boolean demoLoginEnabled() {
        return !"cognito".equalsIgnoreCase(mode);
    }

    public AuthModeStatus status() {
        return cognitoJwtValidator.status(
                mode,
                clientId,
                hostedUiBaseUrl,
                loginUrl(),
                logoutUrl(),
                redirectUri
        );
    }

    private String loginUrl() {
        if (isBlank(hostedUiBaseUrl) || isBlank(clientId) || isBlank(redirectUri)) {
            return "";
        }
        return hostedUiBaseUrl + "/login?client_id=" + encode(clientId)
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&redirect_uri=" + encode(redirectUri);
    }

    private String logoutUrl() {
        if (isBlank(hostedUiBaseUrl) || isBlank(clientId) || isBlank(logoutUri)) {
            return "";
        }
        return hostedUiBaseUrl + "/logout?client_id=" + encode(clientId)
                + "&logout_uri=" + encode(logoutUri);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
