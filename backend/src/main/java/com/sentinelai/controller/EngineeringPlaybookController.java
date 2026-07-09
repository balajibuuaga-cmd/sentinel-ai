package com.sentinelai.controller;

import com.sentinelai.model.playbook.BackendReadinessAssessment;
import com.sentinelai.model.playbook.EngineeringPlaybook;
import com.sentinelai.service.EngineeringPlaybookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/playbooks")
public class EngineeringPlaybookController {

    private final EngineeringPlaybookService engineeringPlaybookService;

    public EngineeringPlaybookController(EngineeringPlaybookService engineeringPlaybookService) {
        this.engineeringPlaybookService = engineeringPlaybookService;
    }

    @GetMapping
    public List<EngineeringPlaybook> playbooks() {
        return engineeringPlaybookService.all();
    }

    @GetMapping("/backend-readiness")
    public BackendReadinessAssessment backendReadiness() {
        return engineeringPlaybookService.backendReadinessAssessment();
    }
}
