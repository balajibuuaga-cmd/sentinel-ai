package com.sentinelai.model;

/**
 * The fixed set of remediation-pipeline steps an operator (or the autonomous
 * pipeline acting on an operator's approval) can execute on an incident. Each
 * executed step is recorded on the incident timeline and in the audit trail.
 */
public enum IncidentRemediationStep {
    ROLLBACK_DEPLOYMENT("Rollback Deployment", "Rollback of the linked deployment was initiated and recorded."),
    RESTART_POD("Restart Pod", "A service restart was initiated and recorded."),
    NOTIFY_SLACK("Notify Slack", "The owning team was notified through the configured channel."),
    OPEN_JIRA("Open Jira", "A tracking ticket was opened for this incident."),
    ASSIGN_ENGINEER("Assign Engineer", "An engineer was assigned as incident responder."),
    MONITOR_RESULTS("Monitor Results", "Post-remediation monitoring was started for the affected service.");

    private final String label;
    private final String detail;

    IncidentRemediationStep(String label, String detail) {
        this.label = label;
        this.detail = detail;
    }

    public String label() {
        return label;
    }

    public String detail() {
        return detail;
    }
}
