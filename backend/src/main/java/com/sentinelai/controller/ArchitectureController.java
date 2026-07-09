package com.sentinelai.controller;

import com.sentinelai.model.ArchitectureBrain;
import com.sentinelai.model.ArchitectureDependency;
import com.sentinelai.model.ArchitectureImportRequest;
import com.sentinelai.model.ArchitectureRisk;
import com.sentinelai.model.ArchitectureServiceNode;
import com.sentinelai.service.ArchitectureBrainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/architecture")
public class ArchitectureController {

    private final ArchitectureBrainService architectureBrainService;

    public ArchitectureController(ArchitectureBrainService architectureBrainService) {
        this.architectureBrainService = architectureBrainService;
    }

    @PostMapping("/import")
    public ArchitectureBrain importArchitecture(@Valid @RequestBody ArchitectureImportRequest request) {
        return architectureBrainService.importArchitecture(request);
    }

    @GetMapping("/services")
    public List<ArchitectureServiceNode> services() {
        return architectureBrainService.services();
    }

    @GetMapping("/dependencies")
    public List<ArchitectureDependency> dependencies() {
        return architectureBrainService.dependencies();
    }

    @GetMapping("/risks")
    public List<ArchitectureRisk> risks() {
        return architectureBrainService.risks();
    }

    @GetMapping("/brain")
    public ArchitectureBrain brain() {
        return architectureBrainService.brain();
    }
}
