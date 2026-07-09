package com.sentinelai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Local email/password (JWT, HS256) and Cognito Hosted UI (JWT, RS256) login are
 * both always available side by side. Incoming bearer tokens are routed to the
 * right validator by their JWT header's "alg", since the two token types are
 * never ambiguous: local tokens are always HS256, Cognito's are always RS256.
 */
@Service
public class TokenAuthenticationService {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final CognitoJwtValidator cognitoJwtValidator;
    private final String clientId;
    private final String hostedUiBaseUrl;
    private final String redirectUri;
    private final String logoutUri;

    public TokenAuthenticationService(
            ObjectMapper objectMapper,
            JwtService jwtService,
            CognitoJwtValidator cognitoJwtValidator,
            @Value("${sentinel.auth.cognito.client-id:}") String clientId,
            @Value("${sentinel.auth.cognito.hosted-ui-base-url:}") String hostedUiBaseUrl,
            @Value("${sentinel.auth.cognito.redirect-uri:}") String redirectUri,
            @Value("${sentinel.auth.cognito.logout-uri:}") String logoutUri
    ) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.cognitoJwtValidator = cognitoJwtValidator;
        this.clientId = clientId;
        this.hostedUiBaseUrl = trimTrailingSlash(hostedUiBaseUrl);
        this.redirectUri = redirectUri;
        this.logoutUri = logoutUri;
    }

    public Optional<AuthenticatedUser> validate(String token) {
        if (isCognitoToken(token)) {
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
        return true;
    }

    private boolean isCognitoToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            Map<String, Object> header = objectMapper.readValue(
                    URL_DECODER.decode(parts[0]),
                    new TypeReference<>() {
                    }
            );
            return "RS256".equals(header.get("alg"));
        } catch (Exception ex) {
            return false;
        }
    }

    public AuthModeStatus status() {
        String mode = (isBlank(hostedUiBaseUrl) || isBlank(clientId)) ? "demo" : "hybrid";
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
