package com.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.repository.IntegrationTokenSecretRepository;
import com.sentinelai.service.integrations.IntegrationTokenVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A secret ref proves nothing on its own. {@code store} derives it from tenant
 * and provider, so it is the same string a never-connected integration carries
 * as a placeholder, and a stored token can outlive the key that encrypted it
 * because the encryption key defaults to the JWT secret.
 *
 * <p>Trusting the ref's prefix made demo connections look syncable, and the sync
 * surfaced "Unable to decrypt integration token" to the user rather than saying
 * the integration was never connected.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.security.rate-limit.auth-requests-per-minute=10000"
        }
)
class IntegrationTokenVaultTests {

    @Autowired
    private IntegrationTokenVault vault;

    @Autowired
    private IntegrationTokenSecretRepository repository;

    @Test
    void aStoredTokenIsReturnedAndRoundTripsThroughEncryption() {
        String ref = vault.store("vault-tenant-a", IntegrationProvider.GITHUB, "gho_realtoken", "");

        assertThat(vault.usableAccessToken(ref)).contains("gho_realtoken");
    }

    @Test
    void aPlaceholderRefWithNoStoredTokenIsNotUsable() {
        // Exactly the shape IntegrationConnectionService assigns to a connection
        // that has never completed an OAuth exchange.
        String placeholder = "db/encrypted/vault-tenant-b/github";

        assertThat(repository.findBySecretRef(placeholder)).isEmpty();
        assertThat(vault.usableAccessToken(placeholder)).isEmpty();
    }

    @Test
    @Transactional
    void aTokenThatCannotBeDecryptedIsReportedAsUnusableRatherThanThrowing() {
        String ref = vault.store("vault-tenant-c", IntegrationProvider.JIRA, "will-be-corrupted", "");
        // Simulate ciphertext written under a different key, which is what a
        // rotated JWT secret leaves behind.
        repository.findBySecretRef(ref).ifPresent(secret -> {
            secret.rotate("bm90LXZhbGlkLWNpcGhlcnRleHQ=", null, "corrupted");
            repository.save(secret);
        });

        assertThat(vault.usableAccessToken(ref)).isEmpty();
    }

    @Test
    void purgeRemovesTheStoredCredential() {
        String ref = vault.store("vault-tenant-d", IntegrationProvider.CI, "token-to-purge", "");
        assertThat(repository.findBySecretRef(ref)).isPresent();

        vault.purge(ref);

        assertThat(repository.findBySecretRef(ref)).isEmpty();
        assertThat(vault.usableAccessToken(ref)).isEmpty();
    }

    @Test
    void purgeToleratesAMissingOrBlankRef() {
        vault.purge(null);
        vault.purge("");
        vault.purge("db/encrypted/never-stored/github");
    }
}
