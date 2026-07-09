package com.sentinelai.controller;

import com.sentinelai.model.Incident;
import com.sentinelai.model.IncidentRemediationStepRequest;
import com.sentinelai.model.IncidentStatusUpdateRequest;
import com.sentinelai.service.IncidentCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentCommandController {

    private final IncidentCommandService incidentCommandService;

    public IncidentCommandController(IncidentCommandService incidentCommandService) {
        this.incidentCommandService = incidentCommandService;
    }

    @GetMapping
    public List<Incident> activeIncidents() {
        return incidentCommandService.activeIncidents();
    }

    @PostMapping("/{id}/status")
    public Incident updateStatus(
            @PathVariable long id,
            @Valid @RequestBody IncidentStatusUpdateRequest request
    ) {
        return incidentCommandService.updateStatus(id, request);
    }

    @PostMapping("/{id}/remediation-step")
    public Incident executeRemediationStep(
            @PathVariable long id,
            @Valid @RequestBody IncidentRemediationStepRequest request
    ) {
        return incidentCommandService.executeRemediationStep(id, request);
    }
}
