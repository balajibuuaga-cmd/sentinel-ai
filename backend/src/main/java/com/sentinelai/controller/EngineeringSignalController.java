package com.sentinelai.controller;

import com.sentinelai.model.CiSignalRequest;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.JiraSignalRequest;
import com.sentinelai.service.EngineeringSignalIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class EngineeringSignalController {

    private final EngineeringSignalIngestionService ingestionService;

    public EngineeringSignalController(EngineeringSignalIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ci/simulate")
    public Deployment simulateCi(@Valid @RequestBody CiSignalRequest request) {
        return ingestionService.ingestCi(request);
    }

    @PostMapping("/jira/simulate")
    public Deployment simulateJira(@Valid @RequestBody JiraSignalRequest request) {
        return ingestionService.ingestJira(request);
    }
}
