package com.sentinelai.controller;

import com.sentinelai.model.intelligence.AiProviderStatus;
import com.sentinelai.service.AiProviderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiProviderController {

    private final AiProviderService aiProviderService;

    public AiProviderController(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    @GetMapping("/provider")
    public AiProviderStatus provider() {
        return aiProviderService.status();
    }
}
