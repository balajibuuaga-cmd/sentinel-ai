package com.sentinelai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CognitoJwtValidator {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String issuer;
    private final String audience;
    private final String roleClaim;
    private final String tenantIdClaim;
    private final String organizationNameClaim;
    private volatile JwksCache jwksCache = new JwksCache(Map.of(), Instant.EPOCH);

    @Autowired
    public CognitoJwtValidator(
            ObjectMapper objectMapper,
            @Value("${sentinel.auth.cognito.issuer:}") String issuer,
            @Value("${sentinel.auth.cognito.audience:}") String audience,
            @Value("${sentinel.auth.cognito.role-claim:custom:role}") String roleClaim,
            @Value("${sentinel.auth.cognito.tenant-id-claim:custom:tenant_id}") String tenantIdClaim,
            @Value("${sentinel.auth.cognito.organization-name-claim:custom:organization_name}") String organizationNameClaim
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.issuer = trimTrailingSlash(issuer);
        this.audience = audience;
        this.roleClaim = roleClaim;
        this.tenantIdClaim = tenantIdClaim;
        this.organizationNameClaim = organizationNameClaim;
    }

    private CognitoJwtValidator(
            ObjectMapper objectMapper,
            String issuer,
            String audience,
            String roleClaim,
            String tenantIdClaim,
            String organizationNameClaim,
            String jwksJson
    ) throws Exception {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.issuer = trimTrailingSlash(issuer);
        this.audience = audience;
        this.roleClaim = roleClaim;
        this.tenantIdClaim = tenantIdClaim;
        this.organizationNameClaim = organizationNameClaim;
        this.jwksCache = parseJwks(jwksJson);
    }

    public static CognitoJwtValidator withJwks(
            ObjectMapper objectMapper,
            String issuer,
            String audience,
            String roleClaim,
            String tenantIdClaim,
            String organizationNameClaim,
            String jwksJson
    ) throws Exception {
        return new CognitoJwtValidator(
                objectMapper,
                issuer,
                audience,
                roleClaim,
                tenantIdClaim,
                organizationNameClaim,
                jwksJson
        );
    }

    public Optional<AuthenticatedUser> validate(String token) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            Map<String, Object> header = readJsonPart(parts[0]);
            Map<String, Object> claims = readJsonPart(parts[1]);
            if (!"RS256".equals(header.get("alg")) || !(header.get("kid") instanceof String kid)) {
                return Optional.empty();
            }
            if (!claimsAreValid(claims)) {
                return Optional.empty();
            }

            RSAPublicKey publicKey = publicKeyFor(kid);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(URL_DECODER.decode(parts[2]))) {
                return Optional.empty();
            }

            String username = firstString(claims, "email", "username", "cognito:username", "sub");
            String tenantId = firstString(claims, tenantIdClaim, "tenantId", "custom:tenantId");
            if (isBlank(username) || isBlank(tenantId)) {
                return Optional.empty();
            }

            String organizationName = firstString(claims, organizationNameClaim, "organizationName", "custom:organizationName");
            return Optional.of(new AuthenticatedUser(
                    username,
                    normalizeRole(resolveRole(claims)),
                    tenantId,
                    isBlank(organizationName) ? tenantId : organizationName
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public AuthModeStatus status(
            String mode,
            String clientId,
            String hostedUiBaseUrl,
            String loginUrl,
            String logoutUrl,
            String redirectUri
    ) {
        return new AuthModeStatus(
                mode,
                !"cognito".equalsIgnoreCase(mode),
                isConfigured(),
                issuer,
                audience,
                clientId,
                hostedUiBaseUrl,
                loginUrl,
                logoutUrl,
                redirectUri,
                roleClaim,
                tenantIdClaim,
                organizationNameClaim
        );
    }

    private boolean claimsAreValid(Map<String, Object> claims) {
        Number exp = asNumber(claims.get("exp"));
        if (exp == null || exp.longValue() <= Instant.now().getEpochSecond()) {
            return false;
        }
        if (!issuer.equals(claims.get("iss"))) {
            return false;
        }
        if (!audienceMatches(claims.get("aud")) && !audience.equals(claims.get("client_id"))) {
            return false;
        }
        Object tokenUse = claims.get("token_use");
        return tokenUse == null || "id".equals(tokenUse) || "access".equals(tokenUse);
    }

    private boolean audienceMatches(Object aud) {
        if (aud instanceof String value) {
            return audience.equals(value);
        }
        if (aud instanceof List<?> values) {
            return values.stream().anyMatch(audience::equals);
        }
        return false;
    }

    private RSAPublicKey publicKeyFor(String kid) throws Exception {
        JwksCache cache = jwksCache;
        if (cache.expiresAt().isBefore(Instant.now())) {
            cache = refreshJwks();
        }
        Jwk jwk = cache.keys().get(kid);
        if (jwk == null) {
            cache = refreshJwks();
            jwk = cache.keys().get(kid);
        }
        if (jwk == null) {
            throw new IllegalStateException("No Cognito signing key for kid " + kid);
        }
        BigInteger modulus = new BigInteger(1, URL_DECODER.decode(jwk.n()));
        BigInteger exponent = new BigInteger(1, URL_DECODER.decode(jwk.e()));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private synchronized JwksCache refreshJwks() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer + "/.well-known/jwks.json"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Unable to fetch Cognito JWKS");
        }

        jwksCache = parseJwks(response.body());
        return jwksCache;
    }

    private JwksCache parseJwks(String jwksJson) throws Exception {
        JsonNode keysNode = objectMapper.readTree(jwksJson).path("keys");
        Map<String, Jwk> keys = objectMapper.convertValue(keysNode, new TypeReference<List<Jwk>>() {
                })
                .stream()
                .filter(key -> "RSA".equals(key.kty()) && "sig".equals(key.use()))
                .collect(java.util.stream.Collectors.toMap(Jwk::kid, key -> key));
        return new JwksCache(keys, Instant.now().plus(JWKS_CACHE_TTL));
    }

    private Map<String, Object> readJsonPart(String part) throws Exception {
        return objectMapper.readValue(URL_DECODER.decode(part), new TypeReference<>() {
        });
    }

    private String resolveRole(Map<String, Object> claims) {
        String directRole = firstString(claims, roleClaim, "role", "custom:role");
        if (!isBlank(directRole)) {
            return directRole;
        }
        Object groups = claims.get("cognito:groups");
        if (groups instanceof List<?> values && !values.isEmpty()) {
            return String.valueOf(values.get(0));
        }
        return "VIEWER";
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "VIEWER" : role.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "ADMIN", "RELEASE_MANAGER", "VIEWER" -> normalized;
            default -> "VIEWER";
        };
    }

    private String firstString(Map<String, Object> claims, String... names) {
        for (String name : names) {
            Object value = claims.get(name);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }

    private boolean isConfigured() {
        return !isBlank(issuer) && !isBlank(audience);
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

    private record JwksCache(Map<String, Jwk> keys, Instant expiresAt) {
    }

    private record Jwk(String kid, String kty, String use, String n, String e) {
    }
}
