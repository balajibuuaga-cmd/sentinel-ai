package com.sentinelai.controller;

import com.sentinelai.model.intelligence.AiProviderStatus;
import com.sentinelai.model.intelligence.AiUsageSummary;
import com.sentinelai.service.AiProviderService;
import com.sentinelai.service.AiUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiProviderController {

    private final AiProviderService aiProviderService;
    private final AiUsageService aiUsageService;

    public AiProviderController(AiProviderService aiProviderService, AiUsageService aiUsageService) {
        this.aiProviderService = aiProviderService;
        this.aiUsageService = aiUsageService;
    }

    @GetMapping("/provider")
    public AiProviderStatus provider() {
        return aiProviderService.status();
    }

    @GetMapping("/usage")
    public AiUsageSummary usage() {
        return aiUsageService.summary();
    }
}
