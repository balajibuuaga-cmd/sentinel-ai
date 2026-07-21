package com.sentinelai.service.integrations;

import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.IntegrationTokenSecret;
import com.sentinelai.repository.IntegrationTokenSecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class IntegrationTokenVault {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTokenVault.class);

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final IntegrationTokenSecretRepository repository;
    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationTokenVault(
            IntegrationTokenSecretRepository repository,
            @Value("${sentinel.integrations.token-encryption-key:${sentinel.jwt.secret}}") String encryptionKey
    ) throws Exception {
        this.repository = repository;
        this.keySpec = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public String store(String tenantId, IntegrationProvider provider, String accessToken, String refreshToken) {
        String secretRef = "db/encrypted/" + tenantId + "/" + provider.name().toLowerCase(Locale.US);
        String encryptedAccessToken = encrypt(accessToken);
        String encryptedRefreshToken = isBlank(refreshToken) ? null : encrypt(refreshToken);
        String fingerprint = fingerprint(accessToken);
        repository.findBySecretRef(secretRef)
                .ifPresentOrElse(
                        existing -> existing.rotate(encryptedAccessToken, encryptedRefreshToken, fingerprint),
                        () -> repository.save(new IntegrationTokenSecret(
                                secretRef,
                                tenantId,
                                provider,
                                encryptedAccessToken,
                                encryptedRefreshToken,
                                fingerprint,
                                Instant.now()
                        ))
                );
        return secretRef;
    }

    public Optional<String> accessToken(String secretRef) {
        return repository.findBySecretRef(secretRef).map(secret -> decrypt(secret.getEncryptedAccessToken()));
    }

    /**
     * Returns the token only when one is stored and decrypts under the current
     * key, and an empty result otherwise.
     *
     * <p>A secret ref alone proves nothing: {@link #store} derives it from tenant
     * and provider, which is the same string a never-connected integration
     * carries as a placeholder. A stored token can also outlive the key that
     * encrypted it, since the encryption key defaults to the JWT secret and
     * rotating that leaves the old ciphertext behind.
     *
     * <p>Callers deciding whether a live provider call is possible want a usable
     * token, not a plausible-looking reference, so neither case should surface as
     * a decryption error to the user.
     */
    public Optional<String> usableAccessToken(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return Optional.empty();
        }
        Optional<IntegrationTokenSecret> stored = repository.findBySecretRef(secretRef);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            String token = decrypt(stored.get().getEncryptedAccessToken());
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        } catch (RuntimeException ex) {
            // Never log the ciphertext or the ref's tenant beyond what the ref
            // already contains; the operator needs to know it must be reconnected.
            log.warn("Stored integration token at {} could not be decrypted; treating the integration "
                    + "as not connected. Reconnect it to store a fresh token.", secretRef);
            return Optional.empty();
        }
    }

    /** Removes the stored credential so a disconnect does not leave it behind. */
    public void purge(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return;
        }
        repository.findBySecretRef(secretRef).ifPresent(repository::delete);
    }

    public Optional<String> refreshToken(String secretRef) {
        return repository.findBySecretRef(secretRef)
                .map(IntegrationTokenSecret::getEncryptedRefreshToken)
                .filter(value -> !isBlank(value))
                .map(this::decrypt);
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt integration token", ex);
        }
    }

    private String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt integration token", ex);
        }
    }

    private String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint integration token", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
