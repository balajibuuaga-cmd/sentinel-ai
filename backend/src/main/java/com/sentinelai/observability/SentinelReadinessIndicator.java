package com.sentinelai.observability;

import com.sentinelai.RuntimeModeService;
import com.sentinelai.security.TokenAuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("sentinelReadiness")
public class SentinelReadinessIndicator implements HealthIndicator {

    private final TokenAuthenticationService tokenAuthenticationService;
    private final String aiProvider;
    private final boolean realExchangeEnabled;
    private final RuntimeModeService runtimeModeService;

    public SentinelReadinessIndicator(
            TokenAuthenticationService tokenAuthenticationService,
            @Value("${sentinel.ai.provider:deterministic}") String aiProvider,
            @Value("${sentinel.integrations.real-exchange-enabled:false}") boolean realExchangeEnabled,
            RuntimeModeService runtimeModeService
    ) {
        this.tokenAuthenticationService = tokenAuthenticationService;
        this.aiProvider = aiProvider;
        this.realExchangeEnabled = realExchangeEnabled;
        this.runtimeModeService = runtimeModeService;
    }

    @Override
    public Health health() {
        var status = tokenAuthenticationService.status();
        Health.Builder builder = "cognito".equalsIgnoreCase(status.mode()) && !status.cognitoConfigured()
                ? Health.down()
                : Health.up();
        return builder
                .withDetail("authMode", status.mode())
                .withDetail("cognitoConfigured", status.cognitoConfigured())
                .withDetail("aiProvider", aiProvider)
                .withDetail("realIntegrationExchangeEnabled", realExchangeEnabled)
                .withDetail("runtimeMode", runtimeModeService.mode())
                .withDetail("apiEnabled", runtimeModeService.apiEnabled())
                .withDetail("workerEnabled", runtimeModeService.workerEnabled())
                .build();
    }
}
