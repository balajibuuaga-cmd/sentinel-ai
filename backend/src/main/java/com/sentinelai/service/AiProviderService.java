package com.sentinelai.service;

import com.sentinelai.model.intelligence.AiProviderStatus;
import com.sentinelai.service.ai.AnthropicChiefEngineerReasoningProvider;
import com.sentinelai.service.ai.ChiefEngineerReasoningProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiProviderService {

    private final ChiefEngineerReasoningProvider reasoningProvider;
    private final String configuredProvider;
    private final String model;
    private final boolean externalCallsEnabled;

    public AiProviderService(
            ChiefEngineerReasoningProvider reasoningProvider,
            @Value("${sentinel.ai.provider:deterministic}") String configuredProvider,
            @Value("${sentinel.ai.model:deterministic-chief-engineer-v1}") String model,
            @Value("${sentinel.ai.external-calls-enabled:false}") boolean externalCallsEnabled
    ) {
        this.reasoningProvider = reasoningProvider;
        this.configuredProvider = configuredProvider;
        this.model = model;
        this.externalCallsEnabled = externalCallsEnabled;
    }

    public AiProviderStatus status() {
        String resolvedModel = reasoningProvider instanceof AnthropicChiefEngineerReasoningProvider anthropic
                ? anthropic.effectiveModel()
                : model;
        return new AiProviderStatus(
                reasoningProvider.name(),
                configuredProvider,
                resolvedModel,
                externalCallsEnabled
        );
    }
}
