package com.sentinelai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * Loads production secrets from AWS Secrets Manager at boot and layers them in
 * as a high-precedence property source, so placeholders such as
 * {@code ${SENTINEL_JWT_SECRET:...}} resolve from the secret instead of the
 * committed dev defaults.
 *
 * <p>Activation is explicit: nothing happens unless {@code SENTINEL_SECRETS_ID}
 * is set (env var or system property). That keeps local development and the
 * test suite fully offline — they never touch AWS. When the flag IS set, a
 * failure to fetch or parse the secret aborts startup rather than silently
 * falling back to the {@code local-dev-*} defaults, which would put dev
 * credentials into a production process.
 *
 * <p>The secret must be a flat JSON object whose keys are the existing
 * environment-variable names, e.g.
 * <pre>{"SENTINEL_JWT_SECRET":"...","SPRING_DATASOURCE_PASSWORD":"..."}</pre>
 */
public class SecretsManagerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SECRET_ID_PROPERTY = "SENTINEL_SECRETS_ID";
    static final String REGION_PROPERTY = "SENTINEL_SECRETS_REGION";
    static final String PROPERTY_SOURCE_NAME = "sentinelAwsSecretsManager";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Log log;

    // Spring instantiates EnvironmentPostProcessors via a constructor that
    // accepts a DeferredLogFactory, so log output is buffered until the logging
    // system is ready (this runs before logging is initialised).
    public SecretsManagerEnvironmentPostProcessor(org.springframework.boot.logging.DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(SecretsManagerEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String secretId = resolve(environment, SECRET_ID_PROPERTY);
        if (secretId == null || secretId.isBlank()) {
            // No secret configured — local/dev/test path. Stay entirely offline.
            return;
        }

        String secretJson = fetchSecret(secretId, resolve(environment, REGION_PROPERTY));
        Map<String, Object> properties = parseSecretJson(secretJson);
        if (properties.isEmpty()) {
            throw new IllegalStateException(
                    "AWS secret '" + secretId + "' resolved to an empty object; refusing to boot on dev defaults.");
        }

        // addFirst → the secret wins over application.properties defaults and
        // any leftover OS env vars of the same name.
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        log.info("Loaded " + properties.size() + " secret(s) from AWS Secrets Manager id '" + secretId + "'.");
    }

    private String fetchSecret(String secretId, String region) {
        Region awsRegion = (region == null || region.isBlank()) ? DEFAULT_REGION : Region.of(region);
        try (SecretsManagerClient client = SecretsManagerClient.builder().region(awsRegion).build()) {
            return client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build())
                    .secretString();
        } catch (RuntimeException ex) {
            // Explicit prod signal + unreachable secret = hard failure, never a
            // silent fallback to committed dev defaults.
            throw new IllegalStateException(
                    "Failed to load AWS secret '" + secretId + "'; aborting startup rather than using dev defaults.",
                    ex);
        }
    }

    /**
     * Parses a flat JSON object into a property map. Nested objects/arrays are
     * ignored (secrets here are flat key/value pairs). Public for tests.
     */
    public static Map<String, Object> parseSecretJson(String secretJson) {
        if (secretJson == null || secretJson.isBlank()) {
            throw new IllegalStateException("AWS secret payload was empty; refusing to boot on dev defaults.");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(secretJson);
        } catch (Exception ex) {
            throw new IllegalStateException("AWS secret payload was not valid JSON.", ex);
        }
        if (!root.isObject()) {
            throw new IllegalStateException("AWS secret payload must be a flat JSON object.");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            JsonNode value = field.getValue();
            if (value.isValueNode()) {
                properties.put(field.getKey(), value.asText());
            }
        }
        return properties;
    }

    private static String resolve(ConfigurableEnvironment environment, String key) {
        // Property lookup already spans OS env vars and system properties.
        return environment.getProperty(key);
    }

    @Override
    public int getOrder() {
        // Run before the logging system is configured, alongside the other
        // built-in config-loading post-processors.
        return LoggingApplicationListener.DEFAULT_ORDER - 1;
    }
}
