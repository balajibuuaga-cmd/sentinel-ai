package com.sentinelai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelai.config.SecretsManagerEnvironmentPostProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the flat-JSON → property mapping used to load AWS Secrets
 * Manager values. The AWS fetch itself is exercised only in production (guarded
 * by SENTINEL_SECRETS_ID), so these tests focus on the parsing contract that
 * decides what ends up overriding the committed dev defaults.
 */
class SecretsManagerPropertyMappingTests {

    @Test
    void mapsFlatJsonKeysToProperties() {
        Map<String, Object> props = SecretsManagerEnvironmentPostProcessor.parseSecretJson(
                "{\"SENTINEL_JWT_SECRET\":\"real-secret\",\"SPRING_DATASOURCE_PASSWORD\":\"pg-pass\"}");

        assertThat(props)
                .containsEntry("SENTINEL_JWT_SECRET", "real-secret")
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "pg-pass");
    }

    @Test
    void coercesNonStringScalarsToText() {
        Map<String, Object> props = SecretsManagerEnvironmentPostProcessor.parseSecretJson(
                "{\"SENTINEL_RATE_LIMIT_REQUESTS_PER_MINUTE\":240,\"SENTINEL_API_ENABLED\":true}");

        assertThat(props)
                .containsEntry("SENTINEL_RATE_LIMIT_REQUESTS_PER_MINUTE", "240")
                .containsEntry("SENTINEL_API_ENABLED", "true");
    }

    @Test
    void ignoresNestedObjectsAndArrays() {
        Map<String, Object> props = SecretsManagerEnvironmentPostProcessor.parseSecretJson(
                "{\"SENTINEL_JWT_SECRET\":\"ok\",\"nested\":{\"a\":1},\"list\":[1,2]}");

        assertThat(props).containsOnlyKeys("SENTINEL_JWT_SECRET");
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> SecretsManagerEnvironmentPostProcessor.parseSecretJson("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> SecretsManagerEnvironmentPostProcessor.parseSecretJson("{not json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonObjectPayload() {
        assertThatThrownBy(() -> SecretsManagerEnvironmentPostProcessor.parseSecretJson("\"just-a-string\""))
                .isInstanceOf(IllegalStateException.class);
    }
}
