package com.sentinelai.controller;

import com.sentinelai.model.BackgroundJob;
import com.sentinelai.service.BackgroundJobQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class BackgroundJobController {

    private final BackgroundJobQueueService backgroundJobQueueService;

    public BackgroundJobController(BackgroundJobQueueService backgroundJobQueueService) {
        this.backgroundJobQueueService = backgroundJobQueueService;
    }

    @GetMapping
    public List<BackgroundJob> recent() {
        return backgroundJobQueueService.recentForCurrentTenant();
    }

    @PostMapping("/{id}/retry")
    public BackgroundJob retry(@PathVariable long id) {
        return backgroundJobQueueService.retry(id);
    }
}
