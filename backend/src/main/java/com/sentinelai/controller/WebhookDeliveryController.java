package com.sentinelai.controller;

import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.service.WebhookDeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks/deliveries")
public class WebhookDeliveryController {

    private final WebhookDeliveryService webhookDeliveryService;

    public WebhookDeliveryController(WebhookDeliveryService webhookDeliveryService) {
        this.webhookDeliveryService = webhookDeliveryService;
    }

    @GetMapping
    public List<WebhookDelivery> recent() {
        return webhookDeliveryService.recentForCurrentTenant();
    }

    @PostMapping("/{id}/replay")
    public WebhookDelivery replay(@PathVariable long id) {
        return webhookDeliveryService.replay(id);
    }
}
