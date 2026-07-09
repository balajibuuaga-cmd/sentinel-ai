package com.sentinelai.model.intelligence;

public record AiProviderStatus(
        String activeProvider,
        String configuredProvider,
        String model,
        boolean externalCallsEnabled
) {
}
