package com.sentinelai.controller;

import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.IntegrationInstallRequest;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.IntegrationSyncEvent;
import com.sentinelai.service.IntegrationConnectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/integration-connections")
public class IntegrationConnectionController {

    private final IntegrationConnectionService service;

    public IntegrationConnectionController(IntegrationConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<IntegrationConnection> connections() {
        return service.findAll();
    }

    @GetMapping("/sync-history")
    public List<IntegrationSyncEvent> syncHistory() {
        return service.syncHistory();
    }

    @PostMapping("/{provider}/install")
    public IntegrationConnection install(
            @PathVariable IntegrationProvider provider,
            @Valid @RequestBody IntegrationInstallRequest request
    ) {
        return service.install(provider, request);
    }

    @PostMapping("/{id}/sync")
    public IntegrationConnection sync(@PathVariable long id) {
        return service.sync(id);
    }

    @DeleteMapping("/{id}")
    public IntegrationConnection disconnect(@PathVariable long id) {
        return service.disconnect(id);
    }
}
