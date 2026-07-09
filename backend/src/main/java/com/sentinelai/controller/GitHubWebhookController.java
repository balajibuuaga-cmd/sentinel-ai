package com.sentinelai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.GitHubWebhookRequest;
import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.service.GitHubWebhookService;
import com.sentinelai.service.WebhookDeliveryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks/github")
public class GitHubWebhookController {

    private final GitHubWebhookService gitHubWebhookService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public GitHubWebhookController(
            GitHubWebhookService gitHubWebhookService,
            WebhookDeliveryService webhookDeliveryService,
            ObjectMapper objectMapper,
            @Value("${sentinel.github.webhook-secret}") String webhookSecret
    ) {
        this.gitHubWebhookService = gitHubWebhookService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<Deployment> ingestSignedWebhook(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(name = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String body
    ) throws Exception {
        if (!validSignature(signature, body)) {
            return ResponseEntity.status(401).build();
        }

        WebhookDelivery delivery = webhookDeliveryService.recordReceived(
                "github",
                deliveryId == null || deliveryId.isBlank() ? "github-" + UUID.randomUUID() : deliveryId,
                eventType == null || eventType.isBlank() ? "deployment" : eventType,
                body,
                RequestContext.requestId()
        );
        try {
            GitHubWebhookRequest request = objectMapper.readValue(body, GitHubWebhookRequest.class);
            Deployment deployment = gitHubWebhookService.ingest(request);
            webhookDeliveryService.markSucceeded(delivery, deployment);
            return ResponseEntity.ok(deployment);
        } catch (Exception ex) {
            webhookDeliveryService.markFailed(delivery, ex.getMessage());
            if (ex instanceof JsonProcessingException) {
                throw new IllegalArgumentException("Signed GitHub webhook payload is not valid JSON.", ex);
            }
            throw ex;
        }
    }

    @PostMapping("/simulate")
    public Deployment simulate(@Valid @RequestBody GitHubWebhookRequest request) {
        return gitHubWebhookService.ingest(request);
    }

    private boolean validSignature(String signature, String body) throws Exception {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return constantTimeEquals(expected, signature);
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < leftBytes.length; index++) {
            result |= leftBytes[index] ^ rightBytes[index];
        }
        return result == 0;
    }
}
