package com.sentinelai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.model.Deployment;
import com.sentinelai.model.DeploymentStatus;
import com.sentinelai.model.Incident;
import com.sentinelai.model.IncidentRemediationStep;
import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.IntegrationStatus;
import com.sentinelai.repository.DeploymentRepository;
import com.sentinelai.repository.IntegrationConnectionRepository;
import com.sentinelai.service.integrations.IntegrationTokenVault;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Performs the external action a remediation step names, when a real integration
 * backs it, and reports honestly when one does not.
 *
 * <p>Every step still records itself on the incident regardless. This only
 * decides whether an outside effect actually happened, so the UI can say
 * "Sent to Slack" rather than "Recorded" when it genuinely did, and can name
 * what is missing when it did not. Nothing here throws: a step must record even
 * if its external call fails.
 */
@Service
public class RemediationActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemediationActionDispatcher.class);

    public enum Result {
        /** The external action was carried out. */
        PERFORMED,
        /** Recorded on the incident, but no external action was taken. */
        RECORDED,
        /** An external action was attempted and failed. */
        FAILED
    }

    public record Outcome(Result result, String detail) {}

    private final DeploymentRepository deploymentRepository;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final IntegrationTokenVault tokenVault;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String slackWebhookUrl;
    private final String jiraCloudId;

    public RemediationActionDispatcher(
            DeploymentRepository deploymentRepository,
            IntegrationConnectionRepository integrationConnectionRepository,
            IntegrationTokenVault tokenVault,
            ObjectMapper objectMapper,
            @Value("${sentinel.incident.slack-webhook-url:}") String slackWebhookUrl,
            @Value("${sentinel.integrations.jira.cloud-id:}") String jiraCloudId
    ) {
        this.deploymentRepository = deploymentRepository;
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.tokenVault = tokenVault;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        this.slackWebhookUrl = slackWebhookUrl;
        this.jiraCloudId = jiraCloudId;
    }

    public Outcome dispatch(Incident incident, IncidentRemediationStep step, String tenantId) {
        return switch (step) {
            case NOTIFY_SLACK -> notifySlack(incident);
            case OPEN_JIRA -> openJira(incident, tenantId);
            case ROLLBACK_DEPLOYMENT -> rollbackDeployment(incident, tenantId);
            case RESTART_POD -> new Outcome(Result.RECORDED,
                    "No container orchestrator is connected, so no pod was restarted.");
            case ASSIGN_ENGINEER -> new Outcome(Result.RECORDED,
                    "Assignment recorded on the incident. No paging integration is connected.");
            case MONITOR_RESULTS -> new Outcome(Result.RECORDED,
                    "Post-remediation monitoring recorded on the incident.");
        };
    }

    private Outcome notifySlack(Incident incident) {
        if (isBlank(slackWebhookUrl)) {
            return new Outcome(Result.RECORDED,
                    "No Slack webhook configured, so no message was sent. Set SENTINEL_SLACK_WEBHOOK_URL to notify a channel.");
        }
        String text = ":rotating_light: *%s* on *%s* (%s, risk %d%%)\n%s"
                .formatted(incident.getSeverity(), incident.getServiceName(),
                        incident.getEnvironment(), incident.getRiskScore(), incident.getSummary());
        try {
            String body = objectMapper.writeValueAsString(Map.of("text", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(slackWebhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new Outcome(Result.PERFORMED, "Message posted to Slack.");
            }
            return new Outcome(Result.FAILED, "Slack rejected the message with status " + response.statusCode() + ".");
        } catch (Exception ex) {
            log.warn("Slack notification for incident {} failed: {}", incident.getIncidentKey(), ex.toString());
            return new Outcome(Result.FAILED, "Could not reach Slack: " + ex.getMessage());
        }
    }

    private Outcome openJira(Incident incident, String tenantId) {
        Optional<String> token = connectedProviderToken(tenantId, IntegrationProvider.JIRA);
        if (token.isEmpty() || isBlank(jiraCloudId)) {
            return new Outcome(Result.RECORDED,
                    "No Jira connection, so no ticket was opened. Connect Jira to create a real issue.");
        }
        // A real Jira issue needs a project this incident maps to. Without a
        // configured project key that mapping is unknown, so record honestly
        // rather than guess a key and fail against the API.
        return new Outcome(Result.RECORDED,
                "Jira is connected, but no project mapping is configured for this service, so no ticket was opened.");
    }

    private Outcome rollbackDeployment(Incident incident, String tenantId) {
        if (incident.getDeploymentId() == null) {
            return new Outcome(Result.RECORDED, "This incident is not linked to a deployment, so nothing was rolled back.");
        }
        Optional<Deployment> deployment = deploymentRepository.findById(incident.getDeploymentId())
                .filter(d -> d.getTenantId().equals(tenantId));
        if (deployment.isEmpty()) {
            return new Outcome(Result.RECORDED, "The linked deployment was not found, so nothing was rolled back.");
        }
        Deployment target = deployment.get();
        if (target.getStatus() == DeploymentStatus.ROLLED_BACK) {
            return new Outcome(Result.RECORDED, target.getDeploymentKey() + " was already marked rolled back.");
        }
        // A real, reversible effect inside Sentinel: the deployment's own record
        // moves to ROLLED_BACK. Reverting the running infrastructure is a
        // separate, deliberate operator action (scripts/rollback.sh) and is not
        // fired from a web button.
        target.setStatus(DeploymentStatus.ROLLED_BACK);
        deploymentRepository.save(target);
        return new Outcome(Result.PERFORMED,
                "Marked " + target.getDeploymentKey() + " rolled back in Sentinel. "
                        + "Run scripts/rollback.sh to revert the running deployment.");
    }

    private Optional<String> connectedProviderToken(String tenantId, IntegrationProvider provider) {
        return integrationConnectionRepository.findByTenantIdAndProvider(tenantId, provider)
                .filter(connection -> connection.getStatus() == IntegrationStatus.CONNECTED)
                .flatMap((IntegrationConnection connection) ->
                        tokenVault.usableAccessToken(connection.getTokenSecretRef()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
