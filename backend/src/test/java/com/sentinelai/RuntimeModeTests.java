package com.sentinelai;

import com.sentinelai.model.BackgroundJob;
import com.sentinelai.model.BackgroundJobStatus;
import com.sentinelai.model.BackgroundJobType;
import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.repository.BackgroundJobRepository;
import com.sentinelai.repository.WebhookDeliveryRepository;
import com.sentinelai.service.BackgroundJobWorkerService;
import com.sentinelai.service.WebhookDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeModeTests {
}

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.api.enabled=false",
                "sentinel.worker.enabled=true"
        }
)
@AutoConfigureMockMvc
class WorkerModeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeModeService runtimeModeService;

    @Test
    void workerModeBlocksProductApiButKeepsHealthAvailable() throws Exception {
        assertThat(runtimeModeService.mode()).isEqualTo("worker");
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is2xxSuccessful());
    }
}

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "sentinel.jwt.secret=test-secret-with-enough-length",
                "sentinel.github.webhook-secret=test-webhook-secret",
                "sentinel.api.enabled=true",
                "sentinel.worker.enabled=false"
        }
)
class ApiOnlyModeTests {

    @Autowired
    private RuntimeModeService runtimeModeService;

    @Autowired
    private BackgroundJobRepository backgroundJobRepository;

    @Autowired
    private BackgroundJobWorkerService backgroundJobWorkerService;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private WebhookDeliveryService webhookDeliveryService;

    @Test
    void apiOnlyModeDoesNotProcessDueJobs() {
        BackgroundJob job = backgroundJobRepository.save(new BackgroundJob(
                "sentinel-demo",
                "Sentinel Demo",
                BackgroundJobType.WEBHOOK_REPLAY,
                "webhook_delivery",
                999L,
                "github:test-delivery",
                "Runtime mode test job.",
                3,
                Instant.now().minusSeconds(1)
        ));

        backgroundJobWorkerService.processDueJobs();

        BackgroundJob saved = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(runtimeModeService.mode()).isEqualTo("api");
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(saved.getAttempts()).isZero();
    }

    @Test
    void cleanupRemovesExpiredCompletedWebhookDeliveries() {
        WebhookDelivery delivery = new WebhookDelivery(
                "sentinel-demo",
                "Sentinel Demo",
                "github",
                "expired-delivery",
                "pull_request",
                "{}",
                "cleanup-test"
        );
        delivery.succeed("GH-expired");
        delivery.applyReplayPolicy(3, Instant.now().minusSeconds(2 * 24 * 60 * 60));
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);

        webhookDeliveryService.cleanupExpiredDeliveries();

        assertThat(webhookDeliveryRepository.findById(saved.getId())).isEmpty();
    }
}
