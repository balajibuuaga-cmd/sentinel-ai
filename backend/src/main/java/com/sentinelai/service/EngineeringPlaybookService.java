package com.sentinelai.service;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.Signal;
import com.sentinelai.model.playbook.BackendReadinessAssessment;
import com.sentinelai.model.playbook.BackendReadinessCheck;
import com.sentinelai.model.playbook.EngineeringPlaybook;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EngineeringPlaybookService {

    private final List<EngineeringPlaybook> playbooks = List.of(
            new EngineeringPlaybook(
                    "production-backend",
                    "Production Backend Readiness",
                    "Backend Architecture",
                    "A production backend should have clear request flow, validation, business logic boundaries, durable storage, tests, deployment automation, and operational visibility.",
                    List.of(
                            "API routes are authenticated, validated, and rate limited.",
                            "Business logic is separated from transport concerns.",
                            "Database access uses migrations, indexes, and transaction boundaries.",
                            "Logging, monitoring, API docs, tests, CI/CD, and deployment rollback paths exist.",
                            "Secrets are externalized and never shipped in code or client bundles."
                    ),
                    List.of(
                            "Block production releases that combine failed CI with database or payment-path changes.",
                            "Surface missing readiness controls in the Operator Console.",
                            "Open incidents when release risk crosses the production threshold."
                    )
            ),
            new EngineeringPlaybook(
                    "api-auth-security",
                    "API, Auth, And Security Controls",
                    "Security",
                    "Secure APIs combine authentication, authorization, validation, safe errors, CORS policy, rate limiting, CSRF/XSS defenses where applicable, and secret management.",
                    List.of(
                            "JWT or SSO tokens are validated server-side with issuer, audience, expiry, and role claims.",
                            "RBAC protects privileged actions such as approvals, provider syncs, and incident transitions.",
                            "Inputs are schema-validated before business logic runs.",
                            "Errors include request IDs but do not leak secrets, stack traces, or provider tokens.",
                            "Audit events preserve who changed what and why."
                    ),
                    List.of(
                            "Attach request IDs to API errors, logs, audit events, and operator views.",
                            "Prefer Cognito or enterprise SSO for production auth.",
                            "Treat auth misconfiguration as a readiness blocker."
                    )
            ),
            new EngineeringPlaybook(
                    "cache-scale",
                    "Caching And Scale Failure Modes",
                    "Scalability",
                    "Cache systems improve latency but can fail through hot-key expiry, cache penetration, herd effects, or full cache outage.",
                    List.of(
                            "Hot keys have protected refresh or no synchronized expiry.",
                            "Unknown keys use null caching or a bloom-filter style guard.",
                            "Cache outages have circuit breakers and graceful degradation.",
                            "Scale plans include load balancing, retry policy, idempotency, and backpressure.",
                            "Database scaling plans account for sharding keys, pagination, and query patterns."
                    ),
                    List.of(
                            "Raise risk when a release touches cache keys, retry behavior, or high-traffic APIs.",
                            "Recommend rollback or circuit breaking when cache errors correlate with database load.",
                            "Add scaling concerns to incident affected systems."
                    )
            ),
            new EngineeringPlaybook(
                    "data-layer",
                    "Database And Data Layer Discipline",
                    "Data Architecture",
                    "Reliable data systems need clear models, migration safety, indexing, lock awareness, sharding strategy, and recovery posture.",
                    List.of(
                            "Migrations are reviewed for locks, backfills, and rollback safety.",
                            "Critical tables have indexes aligned with query plans.",
                            "Transactions protect invariants without over-locking hot paths.",
                            "Sharding or partitioning decisions use high-cardinality, low-hotspot keys.",
                            "Recovery and replication plans are tested before production incidents."
                    ),
                    List.of(
                            "Increase risk for production database migrations touching tier-1 services.",
                            "Ask for query-plan and rollback proof before approving high-risk database releases.",
                            "Connect data-layer changes to architecture blast-radius findings."
                    )
            ),
            new EngineeringPlaybook(
                    "cloud-devops",
                    "Cloud, CI/CD, And Deployment Confidence",
                    "Cloud Operations",
                    "Production systems need repeatable deployments, container/runtime discipline, cloud security controls, automated testing, and rollback workflows.",
                    List.of(
                            "CI covers unit, integration, API, and end-to-end tests appropriate to the release.",
                            "Deployment pipelines preserve auditability and environment separation.",
                            "Cloud controls cover identity, encryption, private networking, WAF, logging, and vulnerability scans.",
                            "Container and Kubernetes changes are observable and reversible.",
                            "Disaster recovery objectives are explicit for critical services."
                    ),
                    List.of(
                            "Treat failed CI as a release-blocking signal when production impact exists.",
                            "Map AWS-ready controls into the Security Posture panel.",
                            "Use deployment decisions as evidence for compliance exports."
                    )
            ),
            new EngineeringPlaybook(
                    "observability-incident",
                    "Observability And Incident Response",
                    "Incident Response",
                    "Incidents need fast detection, ownership, severity, timeline, affected systems, mitigation actions, and post-incident learning.",
                    List.of(
                            "Logs, metrics, traces, health checks, and request IDs can be correlated.",
                            "Severity and owner are assigned immediately.",
                            "Timeline records detection, investigation, mitigation, and resolution.",
                            "Runbooks define rollback, circuit breaker, and communication steps.",
                            "Lessons learned update future release review criteria."
                    ),
                    List.of(
                            "Open incident command when production risk crosses threshold.",
                            "Use timeline events as audit and learning memory.",
                            "Recommend next actions based on strongest signals and blast radius."
                    )
            )
    );

    public List<EngineeringPlaybook> all() {
        return playbooks;
    }

    public BackendReadinessAssessment backendReadinessAssessment() {
        List<BackendReadinessCheck> checks = List.of(
                new BackendReadinessCheck(
                        "API framework and request path",
                        "IMPLEMENTED",
                        95,
                        "Spring Boot REST controllers expose deployments, auth, incidents, integrations, playbooks, operator console, architecture, and AI command routes.",
                        "OpenAPI is currently a lightweight contract rather than a generated schema.",
                        "Expand docs/openapi.yaml with full schemas and examples for every request and response."
                ),
                new BackendReadinessCheck(
                        "Authentication and authorization",
                        "IMPLEMENTED",
                        92,
                        "JWT demo auth, Cognito-ready RS256 validation, tenant claims, RBAC, OAuth state validation, and privileged route guards are in place.",
                        "Production mode still depends on external Cognito environment configuration.",
                        "Add a startup readiness gate that refuses production mode when Cognito issuer, audience, and client secret are missing."
                ),
                new BackendReadinessCheck(
                        "Validation and safe API errors",
                        "IMPLEMENTED",
                        90,
                        "Jakarta validation, structured API errors, request IDs, safe messages, and correlation headers are implemented.",
                        "Not every domain request has rich field-level validation yet.",
                        "Add stricter request DTO validation for architecture imports, provider callbacks, and incident updates."
                ),
                new BackendReadinessCheck(
                        "Database and persistence",
                        "IMPLEMENTED",
                        90,
                        "JPA persistence, tenant-scoped repositories, PostgreSQL Docker Compose support, H2 local mode, encrypted token storage, Flyway migrations, and Hibernate schema validation exist.",
                        "Schema coverage now has a first migration; future model changes still need disciplined versioned migrations.",
                        "Add migration review checks to CI so entity changes and SQL migrations stay synchronized."
                ),
                new BackendReadinessCheck(
                        "Security controls and secrets",
                        "IMPLEMENTED",
                        88,
                        "Security headers, rate limiting, webhook HMAC verification, AES-GCM provider token vault, RBAC, audit trail, and AWS security plan are present.",
                        "Rate limiting is in-memory and secrets are env-backed rather than managed by AWS Secrets Manager locally.",
                        "Add Redis-backed rate limits and production secret loading from AWS Secrets Manager."
                ),
                new BackendReadinessCheck(
                        "Observability and incident response",
                        "IMPLEMENTED",
                        90,
                        "Actuator health/metrics, Micrometer counters, structured operational logs, X-Request-ID correlation, readiness health, and live incident command center exist.",
                        "No distributed tracing exporter is configured yet.",
                        "Add OpenTelemetry traces and dashboard-ready metrics for provider syncs, auth failures, and incident transitions."
                ),
                new BackendReadinessCheck(
                        "Integrations and webhooks",
                        "IMPLEMENTED",
                        93,
                        "GitHub webhook simulation, HMAC ingestion, persisted delivery tracking, dead-letter replay jobs, replay cooldowns, retry budgets, retention cleanup, GitHub/Jira/CI connection state, OAuth install flow, encrypted provider tokens, and live/simulated sync mode exist.",
                        "Provider sync is functional but still narrow in provider coverage.",
                        "Add richer GitHub/Jira/CI provider adapters and provider-specific webhook event mapping."
                ),
                new BackendReadinessCheck(
                        "Caching and scalability",
                        "PARTIAL",
                        68,
                        "The backend has a clean stateless API shape, Redis-backed distributed rate limiting when enabled, and an in-memory local fallback.",
                        "Hot-read caching, cache health dashboards, and cache circuit-breaker policies are not implemented yet.",
                        "Add Redis-backed hot-read caching, cache health checks, and circuit-breaker behavior for cache outages."
                ),
                new BackendReadinessCheck(
                        "Background jobs",
                        "IMPLEMENTED",
                        91,
                        "Persistent background_jobs storage, retry budgets, worker-mode scheduled processing, provider sync retry jobs, incident follow-up jobs, webhook replay jobs, dead-letter cleanup, operator visibility, Docker worker service, and Terraform worker service now exist.",
                        "Worker autoscaling alarms and queue-depth scaling policies are not implemented yet.",
                        "Add worker autoscaling alarms, queue-depth scaling policies, and replay throughput dashboards."
                ),
                new BackendReadinessCheck(
                        "Deployment and cloud readiness",
                        "PARTIAL",
                        82,
                        "Dockerfile, Docker Compose API/worker services, AWS Terraform API/worker ECS services, RDS, ALB, WAF-oriented architecture, health checks, and docs are present.",
                        "Terraform cannot be verified in this environment because Terraform is not installed, and CI/CD workflows are not complete.",
                        "Add GitHub Actions for tests, image build, IaC checks, and deployment promotion gates."
                )
        );

        int score = Math.round((float) checks.stream().mapToInt(BackendReadinessCheck::score).sum() / checks.size());
        return new BackendReadinessAssessment(
                score,
                maturity(score),
                "Sentinel AI now implements most of the backend shipping checklist from the reference document: API framework, auth, RBAC, validation, persistence, security, observability, incident response, Redis-backed rate limiting, durable background jobs, webhook dead-letter replay with throttling and retention, separate API/worker runtime modes, and cloud-ready deployment. The remaining gaps are production migration operations, hot-read caching, worker autoscaling policy, and CI/CD hardening.",
                checks,
                nextActions(checks)
        );
    }

    public List<EngineeringPlaybook> relevantFor(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.US);
        return playbooks.stream()
                .filter(playbook -> matches(playbook, normalized))
                .limit(3)
                .toList();
    }

    public String answer(String question, Deployment deployment) {
        List<EngineeringPlaybook> relevant = relevantFor(question);
        if (relevant.isEmpty() && deployment != null) {
            relevant = relevantFor(deployment.getServiceName() + " " + deployment.getPullRequestTitle() + " "
                    + deployment.getSignals().stream().map(Signal::title).reduce("", (left, right) -> left + " " + right));
        }
        if (relevant.isEmpty()) {
            relevant = playbooks.stream().limit(3).toList();
        }

        StringBuilder answer = new StringBuilder("I mapped this to Sentinel's engineering playbooks: ");
        for (int i = 0; i < relevant.size(); i++) {
            EngineeringPlaybook playbook = relevant.get(i);
            if (i > 0) {
                answer.append(" ");
            }
            answer.append(playbook.title()).append(" says ").append(playbook.summary()).append(" ");
            answer.append("My next checks: ").append(String.join("; ", playbook.checks().stream().limit(3).toList())).append(".");
        }

        if (deployment != null && deployment.getRiskAssessment() != null) {
            RiskAssessment risk = deployment.getRiskAssessment();
            answer.append(" For ").append(deployment.getServiceName())
                    .append(", current risk is ").append(risk.score())
                    .append("%, so I would prioritize ")
                    .append(relevant.stream()
                            .flatMap(playbook -> playbook.sentinelActions().stream())
                            .min(Comparator.comparingInt(String::length))
                            .orElse("tightening the release evidence before approval"))
                    .append(".");
        }
        return answer.toString();
    }

    private String maturity(int score) {
        if (score >= 88) {
            return "Production-leaning";
        }
        if (score >= 75) {
            return "Strong prototype";
        }
        return "Needs hardening";
    }

    private List<String> nextActions(List<BackendReadinessCheck> checks) {
        List<String> actions = new ArrayList<>();
        checks.stream()
                .filter(check -> !"IMPLEMENTED".equals(check.status()))
                .sorted(Comparator.comparingInt(BackendReadinessCheck::score))
                .limit(5)
                .map(BackendReadinessCheck::nextAction)
                .forEach(actions::add);
        return actions;
    }

    private boolean matches(EngineeringPlaybook playbook, String normalized) {
        String haystack = (playbook.id() + " " + playbook.title() + " " + playbook.category() + " "
                + playbook.summary() + " " + String.join(" ", playbook.checks())).toLowerCase(Locale.US);
        return normalized.lines()
                .flatMap(line -> List.of(line.split("[^a-z0-9]+")).stream())
                .filter(token -> token.length() > 3)
                .anyMatch(haystack::contains);
    }
}
