package com.sentinelai.controller;

import com.sentinelai.model.intelligence.CommandRequest;
import com.sentinelai.model.intelligence.CommandResponse;
import com.sentinelai.model.intelligence.DeploymentMemory;
import com.sentinelai.model.intelligence.EngineeringDna;
import com.sentinelai.model.intelligence.ExecutiveBriefing;
import com.sentinelai.service.IntelligenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IntelligenceController {

    private final IntelligenceService intelligenceService;

    public IntelligenceController(IntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @GetMapping("/briefing/executive")
    public ExecutiveBriefing executiveBriefing() {
        return intelligenceService.executiveBriefing();
    }

    @GetMapping("/briefing/memory/{deploymentId}")
    public DeploymentMemory deploymentMemory(@PathVariable long deploymentId) {
        return intelligenceService.deploymentMemory(deploymentId);
    }

    @GetMapping("/engineering-dna")
    public EngineeringDna engineeringDna() {
        return intelligenceService.engineeringDna();
    }

    @PostMapping("/ai/command")
    public CommandResponse answerCommand(@Valid @RequestBody CommandRequest request) {
        return intelligenceService.answerCommand(request.command(), request.deploymentId());
    }
}
