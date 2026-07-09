package com.sentinelai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public JwtService(ObjectMapper objectMapper, @Value("${sentinel.jwt.secret}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(DemoUser user) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.username());
            payload.put("role", user.role());
            payload.put("tenantId", user.tenantId());
            payload.put("organizationName", user.organizationName());
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", Instant.now().plusSeconds(8 * 60 * 60).getEpochSecond());

            String signingInput = encode(header) + "." + encode(payload);
            return signingInput + "." + sign(signingInput);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue JWT", ex);
        }
    }

    public Optional<DemoUser> validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!MessageDigests.equals(sign(signingInput), parts[2])) {
                return Optional.empty();
            }

            Map<String, Object> claims = objectMapper.readValue(
                    URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            Number exp = (Number) claims.get("exp");
            if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            return Optional.of(new DemoUser(
                    (String) claims.get("sub"),
                    "",
                    (String) claims.get("role"),
                    defaultString((String) claims.get("tenantId"), TenantContext.DEFAULT_TENANT_ID),
                    defaultString((String) claims.get("organizationName"), TenantContext.DEFAULT_ORGANIZATION_NAME)
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String encode(Map<String, Object> value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class MessageDigests {
        private static boolean equals(String left, String right) {
            byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
            byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
            if (leftBytes.length != rightBytes.length) {
                return false;
            }
            int result = 0;
            for (int index = 0; index < leftBytes.length; index++) {
                result |= leftBytes[index] ^ rightBytes[index];
            }
            return result == 0;
        }
    }
}
