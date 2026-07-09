package com.sentinelai.controller;

import com.sentinelai.model.ApprovalRequest;
import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.Deployment;
import com.sentinelai.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping("/deployments")
    public List<Deployment> deployments() {
        return deploymentService.findAll();
    }

    @GetMapping("/deployments/{id}")
    public ResponseEntity<Deployment> deployment(@PathVariable long id) {
        return deploymentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/deployments/{id}/analyze")
    public Deployment analyze(@PathVariable long id) {
        return deploymentService.analyze(id);
    }

    @PostMapping("/deployments/{id}/approval")
    public Deployment decide(@PathVariable long id, @Valid @RequestBody ApprovalRequest request) {
        return deploymentService.decide(id, request);
    }

    @GetMapping("/audit-events")
    public List<AuditEvent> auditEvents() {
        return deploymentService.auditEvents();
    }

    @GetMapping("/security/aws-controls")
    public Map<String, List<String>> awsControls() {
        return Map.of(
                "identity", List.of("Amazon Cognito", "IAM least privilege", "MFA-ready admin access"),
                "dataProtection", List.of("AWS KMS", "Secrets Manager", "RDS encryption"),
                "networkProtection", List.of("AWS WAF", "private subnets", "HTTPS-only ingress"),
                "detection", List.of("GuardDuty", "Security Hub", "CloudWatch alarms"),
                "audit", List.of("CloudTrail", "application audit events", "deployment approval history"),
                "vulnerabilityManagement", List.of("Amazon Inspector", "container image scanning", "dependency scanning")
        );
    }
}
