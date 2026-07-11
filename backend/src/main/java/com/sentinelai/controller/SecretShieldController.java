package com.sentinelai.controller;

import com.sentinelai.model.security.SecretScanRequest;
import com.sentinelai.model.security.SecretScanResponse;
import com.sentinelai.service.security.SecretShieldService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecretShieldController {

    private final SecretShieldService secretShieldService;

    public SecretShieldController(SecretShieldService secretShieldService) {
        this.secretShieldService = secretShieldService;
    }

    @PostMapping("/secret-scan")
    public SecretScanResponse scan(@Valid @RequestBody SecretScanRequest request) {
        return secretShieldService.scan(request);
    }
}
