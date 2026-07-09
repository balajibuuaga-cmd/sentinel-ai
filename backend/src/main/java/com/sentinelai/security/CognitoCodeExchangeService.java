package com.sentinelai.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class CognitoCodeExchangeService {

    private final ObjectMapper objectMapper;
    private final CognitoJwtValidator cognitoJwtValidator;
    private final HttpClient httpClient;
    private final String hostedUiBaseUrl;
    private final String clientId;
    private final String clientSecret;

    public CognitoCodeExchangeService(
            ObjectMapper objectMapper,
            CognitoJwtValidator cognitoJwtValidator,
            @Value("${sentinel.auth.cognito.hosted-ui-base-url:}") String hostedUiBaseUrl,
            @Value("${sentinel.auth.cognito.client-id:}") String clientId,
            @Value("${sentinel.auth.cognito.client-secret:}") String clientSecret
    ) {
        this.objectMapper = objectMapper;
        this.cognitoJwtValidator = cognitoJwtValidator;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.hostedUiBaseUrl = trimTrailingSlash(hostedUiBaseUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public Optional<AuthResponse> exchange(CognitoExchangeRequest request) {
        if (isBlank(hostedUiBaseUrl) || isBlank(clientId)) {
            return Optional.empty();
        }
        try {
            String body = form("grant_type", "authorization_code")
                    + "&" + form("client_id", clientId)
                    + "&" + form("code", request.code())
                    + "&" + form("redirect_uri", request.redirectUri());

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(hostedUiBaseUrl + "/oauth2/token"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!isBlank(clientSecret)) {
                requestBuilder.header("Authorization", basicAuth());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode tokenPayload = objectMapper.readTree(response.body());
            String bearerToken = tokenPayload.path("id_token").asText(tokenPayload.path("access_token").asText(""));
            if (isBlank(bearerToken)) {
                return Optional.empty();
            }
            return cognitoJwtValidator.validate(bearerToken)
                    .map(user -> new AuthResponse(
                            bearerToken,
                            user.username(),
                            user.role(),
                            user.tenantId(),
                            user.organizationName()
                    ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String basicAuth() {
        String value = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
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
