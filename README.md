# Sentinel AI

AI Chief Engineer for deployment risk intelligence.

Sentinel AI answers one high-value question:

> Is this deployment risky before it reaches production?

The MVP correlates deployment signals from GitHub, Jira, CI, logs, service dependencies, and incident history to produce a conversational release judgment, risk score, plain-English explanation, recommendation, approval decision, and audit trail.

## Product Wedge

Most engineering tools tell teams what happened after production breaks. Sentinel AI feels like an AI Chief Engineer that reviews the organization continuously before production breaks:

- Which deployment is risky?
- Why is it risky?
- Which services may be affected?
- Should release managers approve, delay, or block it?
- Who approved the final decision?
- What would the AI Chief Engineer do next?

## MVP Features

- Conversational AI Chief Engineer console
- Polished product entry page for investor and customer demos
- In-app demo mode checklist for the flagship talk track
- Daily-style release briefing
- AI executive briefing with projected risk savings
- Durable AI memory timeline for recurring deployment patterns
- AI Engineer mode for pull request merge/wait/block recommendations
- Architecture Brain for service maps, dependency edges, and architecture risk detection
- CI and Jira signal ingestion for build/test/work-context risk
- Production-style integration installation flows for GitHub, Jira, and CI providers
- Customer-facing integration health and sync history
- App-level rate limiting and security headers
- Request correlation, structured operational logs, health details, and metrics
- Production-selectable Cognito JWT validation with tenant-aware RBAC
- AI Chief Engineer reasoning provider abstraction
- Engineering DNA scoring for maturity, velocity, resilience, ownership, and review discipline
- Risk score and severity level
- Evidence-based explanations
- OpenAPI spec in `docs/openapi.yaml`
- Service dependency impact
- Approval workflow
- Immutable-style audit event stream
- AWS security controls map
- Built-in demo data

## Current Architecture

```text
Browser Chief Engineer Console
  -> Spring Boot REST API
  -> Demo JWT or Cognito JWT/RBAC security layer
  -> PostgreSQL or local H2 data store
  -> Risk scoring service
  -> Approval/audit service
  -> Signed GitHub webhook ingestion
  -> CI and Jira signal ingestion
  -> Tenant-scoped integration installation state
  -> Integration health scheduler and sync history
  -> AI reasoning provider abstraction
  -> Persistent engineering memory
```

The browser UI is intentionally not chart-first. It is designed as a command center where the primary interaction is asking Sentinel what it thinks about a release, then approving or blocking based on its evidence. The app runs with H2 by default for quick local development and switches to PostgreSQL through Docker Compose environment variables.

## Run Locally

From the repository root:

```bash
./mvnw -f backend/pom.xml spring-boot:run
```

Open:

```text
http://localhost:8090
```

Optional polished demo entry:

```text
http://localhost:8090/landing.html
```

Health check:

```text
http://localhost:8090/actuator/health
```

Operational metrics:

```text
http://localhost:8090/actuator/metrics
```

Demo logins:

| Role | Username | Password |
|---|---|---|
| Admin | `admin@sentinel.ai` | `sentinel-admin` |
| Release Manager | `release@sentinel.ai` | `sentinel-release` |
| Viewer | `viewer@sentinel.ai` | `sentinel-viewer` |
| Acme tenant demo | `acme-release@sentinel.ai` | `sentinel-acme` |

## Run With PostgreSQL

```bash
docker compose up --build
```

The Compose stack starts PostgreSQL 16, Redis 7, and the Spring Boot app on `http://localhost:8090`.

Sentinel uses Flyway migrations as the database source of truth. Hibernate validates the schema at
startup instead of creating or changing production tables automatically. The first boot applies
migrations from `backend/src/main/resources/db/migration`. Existing local development databases
can be baselined with `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, which is enabled by default.

## Auth Modes

Local development defaults to demo JWT login:

```bash
SENTINEL_AUTH_MODE=demo
```

AWS/ECS production is wired for Cognito JWT validation:

```bash
SENTINEL_AUTH_MODE=cognito
SENTINEL_COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/<user-pool-id>
SENTINEL_COGNITO_AUDIENCE=<user-pool-client-id>
SENTINEL_COGNITO_CLIENT_ID=<user-pool-client-id>
SENTINEL_COGNITO_CLIENT_SECRET=<user-pool-client-secret>
SENTINEL_COGNITO_HOSTED_UI_BASE_URL=https://<domain>.auth.us-east-1.amazoncognito.com
SENTINEL_COGNITO_REDIRECT_URI=https://app.example.com/
SENTINEL_COGNITO_LOGOUT_URI=https://app.example.com/
SENTINEL_COGNITO_ROLE_CLAIM=custom:role
SENTINEL_COGNITO_TENANT_ID_CLAIM=custom:tenant_id
SENTINEL_COGNITO_ORGANIZATION_NAME_CLAIM=custom:organization_name
```

In Cognito mode, the browser sends users through Cognito Hosted UI, the Spring Boot API exchanges the authorization code with the confidential client secret, and Sentinel verifies RS256 token signatures from the pool JWKS. It checks issuer, audience, expiry, token use, OAuth state, role claims, and tenant claims, then maps the user into the same RBAC and tenant-scoped data path used by the demo.

## Integration OAuth

Local demos keep simulated provider installs by default:

```bash
SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED=false
```

Production can exchange real provider authorization codes and encrypt the returned tokens in Sentinel's integration token vault:

```bash
SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED=true
SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY=<long-random-secret>
SENTINEL_GITHUB_CLIENT_ID=<github-client-id>
SENTINEL_GITHUB_CLIENT_SECRET=<github-client-secret>
SENTINEL_GITHUB_REDIRECT_URI=https://app.example.com/integrations/github/callback
SENTINEL_JIRA_CLIENT_ID=<jira-client-id>
SENTINEL_JIRA_CLIENT_SECRET=<jira-client-secret>
SENTINEL_JIRA_REDIRECT_URI=https://app.example.com/integrations/jira/callback
SENTINEL_JIRA_CLOUD_ID=<atlassian-cloud-id>
SENTINEL_CI_CLIENT_ID=<ci-client-id>
SENTINEL_CI_CLIENT_SECRET=<ci-client-secret>
SENTINEL_CI_AUTHORIZE_URL=https://ci.example.com/oauth/authorize
SENTINEL_CI_TOKEN_URL=https://ci.example.com/oauth/token
SENTINEL_CI_RUNS_URL=https://ci.example.com/api/runs
SENTINEL_CI_REDIRECT_URI=https://app.example.com/integrations/ci/callback
```

GitHub, Jira, and generic CI callbacks route through `/integrations/{provider}/callback`, return to the signed-in UI, and complete the tenant-checked install through `/api/integration-connections/{provider}/install`.
When a connected provider is synced, Sentinel decrypts the token only inside the backend, fetches recent provider records, and converts them into deployment, Jira, and CI risk signals.

## Runtime Modes

Sentinel can run as one combined local process or as separate API and worker runtimes:

```bash
SENTINEL_API_ENABLED=true
SENTINEL_WORKER_ENABLED=false
```

serves the UI/API without scheduled job processing. Worker runtime uses:

```bash
SENTINEL_API_ENABLED=false
SENTINEL_WORKER_ENABLED=true
```

and processes provider retries, incident follow-ups, and webhook replay while returning 503 for product API routes. Docker Compose now starts both `app` and `worker` services from the same image, and Terraform creates separate ECS services for independent scaling.

Webhook replay is rate-limited with a replay budget, cooldown window, expiry timestamp, and worker cleanup job. The Delivery Replay panel shows whether a delivery is ready, queued, cooling down, expired, or no longer replayable.

## Observability

Sentinel emits `X-Request-ID` on every response. If the client sends `X-Request-ID`, Sentinel preserves it; otherwise it generates one.

Structured operational logs are emitted for:

- deployment approval decisions
- GitHub webhook ingestion
- CI and Jira signal ingestion
- live and simulated provider syncs
- provider sync failures with categories: `AUTH_EXPIRED`, `MISSING_SCOPE`, `RATE_LIMITED`, `PROVIDER_DOWN`, `BAD_CONFIG`, `UNKNOWN`

Actuator metrics include:

- `sentinel_deployment_reviews_created_total`
- `sentinel_approval_decisions_total`
- `sentinel_webhooks_ingested_total`
- `sentinel_provider_sync_attempts_total`
- `sentinel_provider_sync_failures_total`

The readiness health contributor reports auth mode, Cognito configuration, AI provider mode, runtime mode, API enablement, worker enablement, and whether real integration exchange is enabled.

## API Errors

API failures return a consistent JSON body:

```json
{
  "requestId": "abc-123",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": {
    "field": "must not be blank"
  },
  "timestamp": "2026-07-03T23:00:00Z"
}
```

The `requestId` matches the `X-Request-ID` response header so logs, audit details, and UI-reported failures can be correlated.

## Test

```bash
./mvnw -f backend/pom.xml test
```

## Demo

See [DEMO.md](DEMO.md) for the full two-minute talk track.

With the app running:

```bash
scripts/demo.sh
```

## API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/deployments` | List deployment reviews |
| GET | `/api/deployments/{id}` | Get one deployment |
| POST | `/api/deployments/{id}/analyze` | Re-run risk analysis |
| POST | `/api/deployments/{id}/approval` | Approve, block, or request changes |
| GET | `/api/audit-events` | View audit trail |
| GET | `/api/security/aws-controls` | View AWS security control plan |
| POST | `/api/auth/login` | Issue a demo JWT |
| GET | `/api/auth/status` | Show active auth mode and Cognito claim mapping |
| POST | `/api/auth/cognito/exchange` | Exchange a Cognito authorization code for a validated Sentinel session |
| GET | `/api/organization/current` | Show current tenant, onboarding, and workspace status |
| GET | `/api/ai/provider` | Show the active Chief Engineer reasoning provider |
| GET | `/api/playbooks` | Show Sentinel's curated engineering playbooks derived from the backend/system-design reference pack |
| GET | `/api/playbooks/backend-readiness` | Assess Sentinel AI against the backend shipping checklist |
| GET | `/api/incidents` | Show live incident command center items opened from risky production evidence |
| POST | `/api/incidents/{id}/status` | Move an incident through investigation, mitigation, or resolution |
| GET | `/api/operator/console` | Show secured runtime readiness, modes, counters, request ID, and provider failures |
| GET | `/api/jobs` | Show durable background jobs for retries, incident follow-up, and replay work |
| POST | `/api/jobs/{id}/retry` | Requeue a failed durable background job |
| GET | `/api/webhooks/deliveries` | Inspect signed webhook deliveries and dead-letter state |
| POST | `/api/webhooks/deliveries/{id}/replay` | Queue replay for a stored webhook delivery |
| GET | `/api/integration-connections` | List GitHub, Jira, and CI connection state for the tenant |
| GET | `/api/integration-connections/sync-history` | View recent integration health and sync events |
| POST | `/api/integration-connections/{provider}/install` | Complete simulated or real OAuth installation callback |
| POST | `/api/integration-connections/{id}/sync` | Record a successful integration sync |
| DELETE | `/api/integration-connections/{id}` | Disconnect an integration |
| POST | `/api/webhooks/github` | Ingest signed GitHub webhook payload |
| POST | `/api/webhooks/github/simulate` | Simulate webhook ingestion from the authenticated UI |
| POST | `/api/integrations/ci/simulate` | Attach CI build/test evidence to a deployment review |
| POST | `/api/integrations/jira/simulate` | Attach Jira work context to a deployment review |
| GET | `/api/briefing/executive` | Generate Sentinel's executive engineering briefing |
| GET | `/api/briefing/memory/{deploymentId}` | Recall similar operational patterns for a deployment |
| GET | `/api/engineering-dna` | Score engineering maturity, velocity, resilience, ownership, and review discipline |
| POST | `/api/ai/command` | Ask Sentinel a release-risk question |
| POST | `/api/pr-reviews/simulate` | Ask AI Engineer to review a simulated pull request |
| GET | `/api/pr-reviews` | List recent AI Engineer PR reviews |
| GET | `/api/pr-reviews/{id}` | Get one PR review |
| POST | `/api/pr-reviews/{id}/decision` | Record merge, wait, or block decision |
| POST | `/api/architecture/import` | Import services and dependencies for Architecture Brain |
| GET | `/api/architecture/services` | List architecture services |
| GET | `/api/architecture/dependencies` | List dependency edges |
| GET | `/api/architecture/risks` | List detected architecture risks |
| GET | `/api/architecture/brain` | Summarize architecture intelligence |

Signed GitHub webhook payloads use `X-Hub-Signature-256: sha256=<hmac>` with `SENTINEL_GITHUB_WEBHOOK_SECRET`.

## AI Provider Configuration

Sentinel defaults to a deterministic local Chief Engineer provider:

```bash
SENTINEL_AI_PROVIDER=deterministic
SENTINEL_AI_MODEL=deterministic-chief-engineer-v1
SENTINEL_AI_EXTERNAL_CALLS_ENABLED=false
```

The scoring engine stays deterministic, while explanation, recommendation, executive briefing, PR review, and chat answer text route through `ChiefEngineerReasoningProvider`. A hosted LLM provider can be added behind that interface without changing controllers or the product UI.

## Production Hardening

- App-level API rate limiting is enabled with `SENTINEL_RATE_LIMIT_REQUESTS_PER_MINUTE`.
- Redis-backed distributed rate limiting is enabled with `SENTINEL_REDIS_RATE_LIMIT_ENABLED=true`.
- Security headers include CSP, frame denial, content-type protection, referrer policy, and permissions policy.
- Every request receives an `X-Request-ID` correlation header.
- Integration health checks run on `SENTINEL_INTEGRATION_HEALTH_CHECK_DELAY_MS`.
- Database schema changes are versioned with Flyway and checked by Hibernate `validate`.
- GitHub Actions CI lives in `.github/workflows/ci.yml`.
- OpenAPI documentation lives in `docs/openapi.yaml`.

## AWS Security Target

The production version should use:

- Amazon Cognito for customer authentication and RS256 JWT validation
- IAM least privilege for AWS access
- Secrets Manager for GitHub, Jira, DB, and AI provider secrets
- KMS for encryption keys
- RDS PostgreSQL for durable data
- ECS Fargate for backend runtime
- S3 + CloudFront for frontend hosting
- WAF for app/API protection
- CloudWatch for logs, metrics, alarms
- CloudTrail for AWS account audit history
- GuardDuty for threat detection
- Inspector for vulnerability scanning
- Security Hub for centralized findings

Terraform for the AWS baseline lives in [infra/aws](infra/aws). It provisions ECS Fargate, RDS PostgreSQL, Cognito, WAF, Secrets Manager, CloudWatch, IAM, and private networking.

## Next Milestones

1. Add a hosted LLM implementation of `ChiefEngineerReasoningProvider`.
2. Add real provider token exchange clients for GitHub, Jira, and CI.
3. Add Redis-backed hot-read caching and cache health checks.
4. Add worker autoscaling alarms and queue-depth scaling policies.
5. Add hosted frontend login against Cognito's authorization-code flow.
