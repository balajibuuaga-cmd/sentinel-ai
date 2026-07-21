# Sentinel AI

**An AI Chief Engineer for deployment risk intelligence.**

🔗 **Live:** [getsentinelai.dev](https://getsentinelai.dev)

Sentinel AI scores a deployment for risk before you ship it, and shows you the evidence behind the
score.

It reads deployment signals, service dependencies, CI and Jira context, and incident history, then
returns a risk score, an explanation citing the evidence it used, a recommendation, an approval
decision, and an audit trail.

The system runs in production on AWS. A Spring Boot API serves a React console, Amazon Bedrock
(Claude) does the reasoning, and the deploy pipeline rolls itself back when a health check fails.

---

## Table of Contents

- [What's Actually Running](#whats-actually-running)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Security](#security)
- [Run Locally](#run-locally)
- [Testing](#testing)
- [Deployment & Operations](#deployment--operations)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Roadmap](#roadmap)

---

## What's Actually Running

Everything in this table runs in production today. The ECS and WAF architecture under
[Future AWS Target](#future-aws-target) does not.

| Layer | Implementation |
|---|---|
| **Domain / TLS** | `getsentinelai.dev` via Route 53, Let's Encrypt certificate (auto-renewing), Nginx reverse proxy |
| **Compute** | Single EC2 `t4g.micro` running the Spring Boot jar as a systemd service (`sentinel-ai`), fronted by Nginx, on a static Elastic IP |
| **Database** | AWS RDS PostgreSQL (`sentinel-ai-db`) in a **private** subnet, unreachable from the internet |
| **AI reasoning** | **Real AWS Bedrock calls** to Claude (`us.anthropic.claude-sonnet-4-6`) via an IAM instance role scoped to `bedrock:InvokeModel`, with a deterministic fallback provider |
| **Auth** | Hybrid: DB-backed email/password (BCrypt) **+ TOTP MFA**, alongside a wired AWS Cognito SSO path |
| **Email** | AWS SES (`noreply@getsentinelai.dev`) with verified domain identity, DKIM/SPF/DMARC |
| **Monitoring** | Route 53 health check → CloudWatch alarm → SNS email alerting |
| **Secrets** | Loaded from AWS Secrets Manager at boot (opt-in via `SENTINEL_SECRETS_ID`) |
| **Deploys** | `scripts/deploy.sh` builds, ships, restarts, health-checks, and **auto-rolls back** on failure |

**Size:** 202 Java source files, 20 REST controllers, 9 Flyway migrations, 26 React pages,
72 backend tests, 36 Playwright browser tests.

---

## Architecture

```text
                        ┌─────────────────────────────────────┐
                        │   React 19 + TypeScript Console     │
                        │   (Vite, 26 lazy-loaded routes)     │
                        └──────────────────┬──────────────────┘
                                           │  HTTPS
                        ┌──────────────────▼──────────────────┐
                        │      Nginx  +  Let's Encrypt TLS    │
                        └──────────────────┬──────────────────┘
                                           │
      ┌────────────────────────────────────▼────────────────────────────────────┐
      │                     Spring Boot REST API (Java 17+)                     │
      │                                                                         │
      │   Security filter chain                                                 │
      │     ├── Rate limiting (tighter bucket on /api/auth/**)                  │
      │     ├── JWT (HS256) or Cognito RS256 validation                         │
      │     ├── TOTP MFA challenge                                              │
      │     ├── Tenant resolution → TenantContext                               │
      │     └── CSP + security headers, X-Request-ID correlation                │
      │                                                                         │
      │   Domain services                                                       │
      │     ├── Risk scoring          (deterministic, auditable)                │
      │     ├── Chief Engineer AI     ──────────────► Amazon Bedrock (Claude)   │
      │     │     └── deterministic fallback provider                           │
      │     ├── Secret Shield         (scanner + AI risk gates)                 │
      │     ├── Incident command      (per-step remediation pipeline)           │
      │     ├── Approval + audit trail                                          │
      │     ├── Background job worker (retries, replay, follow-ups)             │
      │     ├── AI usage/cost tracking                                          │
      │     └── Error tracking        (in-app, tenant-scoped)                   │
      └────────────────────────────────────┬────────────────────────────────────┘
                                           │
                   ┌───────────────────────┼───────────────────────┐
                   ▼                       ▼                       ▼
        ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
        │ RDS PostgreSQL   │    │  AWS SES         │    │  AWS Cognito     │
        │ (private subnet) │    │  (transactional) │    │  (SSO, optional) │
        │ Flyway V1–V9     │    └──────────────────┘    └──────────────────┘
        └──────────────────┘
```

The console works as a command center rather than a dashboard. You ask Sentinel what it thinks about
a release, read the evidence it cites, then approve or block.

**Why the AI never blocks the product.** Risk scoring is deterministic and auditable. The LLM writes
only the explanation, recommendation, briefing, and review text, behind a
`ChiefEngineerReasoningProvider` interface. When Bedrock is slow, throttled, or down, a deterministic
provider answers in its place, so no user flow waits on the model.

---

## Tech Stack

| Area | Technologies |
|---|---|
| **Backend** | Java 17+, Spring Boot 3.5, Spring Security, Spring Data JPA / Hibernate, Flyway, Jakarta Validation |
| **Frontend** | React 19, TypeScript, Vite, React Router, Recharts, Three.js (`@react-three/fiber`) |
| **Database** | PostgreSQL (prod, AWS RDS) · H2 in-memory (local/tests) |
| **AI** | AWS Bedrock Converse API, Claude Sonnet |
| **AWS** | EC2, RDS, Bedrock, Cognito, SES, Route 53, Secrets Manager, CloudWatch, SNS, IAM, Elastic IP |
| **Testing** | JUnit 5, Spring MockMvc, AssertJ, Playwright |
| **CI/CD** | GitHub Actions, Docker, Terraform, custom deploy/rollback scripts |

---

## Features

### AI Chief Engineer
- Conversational release-risk console: ask a question, get a judgment with its evidence
- Deployment risk analysis with score, severity, and plain-English explanation
- **AI Engineer** mode: merge / wait / block recommendations on pull requests
- Executive briefing with projected risk savings, and a board-ready report view
- Durable **AI memory** timeline that recalls recurring deployment patterns
- **Engineering DNA** scoring across maturity, velocity, resilience, ownership, review discipline
- **Architecture Brain**: service maps, dependency edges, and architecture risk detection
- A deterministic fallback answers when the model is unavailable

### Observability into the AI itself
- Per-request **token usage, latency, and cost** capture for every Bedrock call
- An analytics dashboard that attributes AI spend to the requests that caused it
- Fallback tracking: see when the deterministic path took over, and how often

### Operations
- **Incident command center** with a real per-step remediation pipeline
- Durable **background job queue** for retries, incident follow-ups, and webhook replay
- Signed GitHub webhook ingestion with delivery inspection, dead-letter state, and rate-limited replay
- Integration installation flows (GitHub / Jira / CI) with health and sync history
- **Operator console** exposing runtime readiness, counters, provider failures, and recent server errors
- In-app **error tracking**: the app captures unhandled server errors, scopes them by tenant, and shows them in the UI

### Platform
- **Multi-tenant** isolation enforced at the query layer, with role-based access control
- Immutable-style audit event stream over every consequential action
- Runtime modes: one combined process, or API and worker runtimes you scale on their own
- Structured JSON operational logging with `X-Request-ID` correlation end to end
- Consistent JSON error contract carrying the request ID for log correlation

---

## Security

Each control below ships in the running system. None of it is planned work.

| Control | Implementation |
|---|---|
| **Password storage** | BCrypt (per-password salt), never plaintext |
| **MFA** | TOTP (RFC 6238), authenticator-app compatible |
| **SSO** | AWS Cognito authorization-code flow, RS256 verified against pool JWKS (issuer, audience, expiry, token use, OAuth state) |
| **Brute force** | Per-account lockout after repeated failures, plus a tighter rate-limit bucket on `/api/auth/**` |
| **Tenant isolation** | Every lookup scoped by tenant at the **query layer** (`findByIdAndTenantId`), so a wrong ID returns nothing rather than another tenant's row |
| **Secret leakage** | **Secret Shield**: a deterministic scanner plus AI risk gates. Values are masked before they reach logs or the UI |
| **Secrets at rest** | AWS Secrets Manager loaded at boot; fail-fast if unreachable, so the app refuses to start on dev defaults |
| **Network** | RDS in a private subnet (not publicly accessible); SSH restricted to a single `/32`, auto-rotated by the deploy script |
| **Transport** | TLS everywhere via Let's Encrypt, auto-renewing |
| **Headers** | CSP, frame denial, content-type protection, referrer policy, permissions policy |
| **Error handling** | Global exception handler returns structured errors with **no stack-trace leakage** |
| **Auditability** | Append-only audit events for logins, approvals, and privileged actions |
| **Account enumeration** | Password reset returns the same response whether or not the address is registered, including when email delivery fails |

---

## Run Locally

**Prerequisites:** JDK 17+, Node 20+.

The app runs against in-memory H2 by default with seeded demo data, so no database setup is needed.

### Backend

```bash
./mvnw -f backend/pom.xml spring-boot:run
```

API on `http://localhost:8090` · health at `/actuator/health` · metrics at `/actuator/metrics`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Console on `http://localhost:5173`, proxying `/api` to the backend on 8090.

### With PostgreSQL + Redis

```bash
docker compose up --build
```

Starts PostgreSQL 16, Redis 7, and the app on `http://localhost:8090`.

Flyway migrations own the schema. Hibernate runs in `validate` mode, so it reads the schema and
leaves it alone.

### Demo logins (local only)

The `V5` migration seeds four accounts so a fresh database works on first boot: one per role, plus a
second tenant you can use to check isolation.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@sentinel.ai` | `sentinel-admin` |
| Release Manager | `release@sentinel.ai` | `sentinel-release` |
| Viewer | `viewer@sentinel.ai` | `sentinel-viewer` |
| Second tenant | `acme-release@sentinel.ai` | `sentinel-acme` |

> **These credentials work only against a locally-seeded database.** I rotated them on the hosted
> deployment, so they grant nothing there. Rotate or remove them on any internet-facing environment.
> A seed password published in a public repository is not a credential.

---

## Testing

```bash
# Backend: 72 tests (JUnit + MockMvc, H2)
./mvnw -f backend/pom.xml test

# End-to-end: 36 tests in a real browser against a real backend
cd frontend
npm run test:e2e
```

The Playwright suite starts its own Spring Boot jar and Vite dev server on isolated ports
(`reuseExistingServer: false`), so another app sitting on a shared port cannot stand in for Sentinel.
It then drives login and routing in Chromium against that stack.

**CI** (`.github/workflows/ci.yml`) runs three parallel jobs on every push and PR:

| Job | Checks |
|---|---|
| `test` | Backend test suite, frontend syntax, script syntax, Docker image build |
| `e2e` | Playwright browser tests against a newly built jar |
| `terraform` | `fmt -check`, `init`, and `validate` on `infra/aws` |

---

## Deployment & Operations

```bash
./scripts/deploy.sh     # build → ship → restart → health-check (auto-rollback on failure)
./scripts/rollback.sh   # manual revert to the previous release
```

**How a deploy stays safe:**

1. Builds the backend jar and frontend bundle locally.
2. Detects the operator's current public IP and whitelists it for SSH, revoking the previous `/32`
   so stale addresses don't accumulate as standing exposure.
3. Ships both artifacts, preserving the previous release as `app.jar.prev` / `html.prev`.
4. Restarts the service and polls the health endpoint.
5. **When the health check fails, the script restores the previous release** and exits non-zero.

Supporting scripts: `scripts/setup-monitoring.sh` (Route 53 health check → CloudWatch alarm → SNS)
and `scripts/setup-secrets.sh` (provisions the Secrets Manager secret and prints the least-privilege
IAM policy).

---

## Configuration

All configuration is environment-driven with safe local defaults. Full list in
`backend/src/main/resources/application.properties`.

### Secrets Manager (production)

```bash
SENTINEL_SECRETS_ID=sentinel-ai/prod      # unset locally → no AWS calls at all
SENTINEL_SECRETS_REGION=us-east-1
```

When you set this, the app fetches a flat JSON secret at boot and layers it in as the
highest-precedence property source. Its keys are the environment-variable names already used
elsewhere. Leave it unset and the loader does nothing, so local dev and CI never call AWS.

### AI provider

```bash
SENTINEL_AI_PROVIDER=anthropic                       # or: deterministic
SENTINEL_AI_MODEL=us.anthropic.claude-sonnet-4-6
SENTINEL_AI_EXTERNAL_CALLS_ENABLED=true
```

### Cognito SSO (optional)

```bash
SENTINEL_COGNITO_ISSUER=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>
SENTINEL_COGNITO_AUDIENCE=<client-id>
SENTINEL_COGNITO_CLIENT_ID=<client-id>
SENTINEL_COGNITO_CLIENT_SECRET=<client-secret>
SENTINEL_COGNITO_HOSTED_UI_BASE_URL=https://<domain>.auth.<region>.amazoncognito.com
SENTINEL_COGNITO_REDIRECT_URI=https://app.example.com/auth/cognito/callback
SENTINEL_COGNITO_ROLE_CLAIM=custom:role
SENTINEL_COGNITO_TENANT_ID_CLAIM=custom:tenant_id
```

The frontend only offers the Cognito button when the backend advertises it via `/api/auth/status`.

### Integrations

```bash
SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED=true     # false → simulated installs
SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY=<long-random-secret>
SENTINEL_GITHUB_CLIENT_ID=...
SENTINEL_GITHUB_CLIENT_SECRET=...
SENTINEL_JIRA_CLIENT_ID=...
SENTINEL_CI_CLIENT_ID=...
```

Provider tokens are encrypted in Sentinel's integration vault and decrypted only inside the backend.

### Runtime modes

```bash
SENTINEL_API_ENABLED=true     SENTINEL_WORKER_ENABLED=false   # API only
SENTINEL_API_ENABLED=false    SENTINEL_WORKER_ENABLED=true    # worker only
```

Worker runtime processes provider retries, incident follow-ups, and webhook replay while returning
503 for product API routes.

### Rate limiting & webhooks

```bash
SENTINEL_RATE_LIMIT_REQUESTS_PER_MINUTE=120
SENTINEL_RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE=15
SENTINEL_REDIS_RATE_LIMIT_ENABLED=true
SENTINEL_GITHUB_WEBHOOK_SECRET=<secret>
```

Signed GitHub payloads use `X-Hub-Signature-256: sha256=<hmac>`.

---

## API Reference

Full OpenAPI spec: [`docs/openapi.yaml`](docs/openapi.yaml).

### Auth & account
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/signup` | Create an organization and its first admin |
| POST | `/api/auth/login` | Authenticate, issue a session token |
| GET | `/api/auth/status` | Active auth mode and Cognito claim mapping |
| POST | `/api/auth/cognito/exchange` | Exchange a Cognito authorization code |
| POST | `/api/auth/password-reset/request` | Request a reset link |
| POST | `/api/auth/password-reset/confirm` | Complete a reset |
| GET/POST | `/api/account/mfa/**` | Enroll, verify, and disable TOTP MFA |
| GET | `/api/account/me` | Current profile |
| GET/POST | `/api/team/**` | Invite and manage tenant members |

### Deployments & risk
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/deployments` | List deployment reviews |
| POST | `/api/deployments/{id}/analyze` | Re-run risk analysis |
| POST | `/api/deployments/{id}/approval` | Approve, block, or request changes |
| POST | `/api/pr-reviews/simulate` | Ask AI Engineer to review a pull request |
| POST | `/api/pr-reviews/{id}/decision` | Record merge / wait / block |
| GET | `/api/architecture/brain` | Summarize architecture intelligence |
| GET | `/api/engineering-dna` | Engineering maturity scoring |

### AI
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/ai/command` | Ask Sentinel a release-risk question |
| GET | `/api/ai/provider` | Active reasoning provider |
| GET | `/api/ai/usage` | Token, latency, and cost telemetry |
| GET | `/api/briefing/executive` | Executive engineering briefing |
| GET | `/api/briefing/memory/{deploymentId}` | Recall similar operational patterns |
| POST | `/api/security/secret-scan` | Secret Shield: scan content for exposed secrets |

### Operations
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/incidents` | Incident command center |
| POST | `/api/incidents/{id}/remediation-step` | Execute a remediation step |
| GET | `/api/operator/console` | Runtime readiness and counters |
| GET | `/api/operator/errors` | Recent unhandled server errors |
| GET | `/api/jobs` | Durable background jobs |
| GET | `/api/webhooks/deliveries` | Inspect deliveries and dead-letter state |
| POST | `/api/webhooks/github` | Ingest a signed GitHub webhook |
| GET | `/api/integration-connections` | Tenant integration state |
| GET | `/api/audit-events` | Audit trail |

Errors return a consistent body whose `requestId` matches the `X-Request-ID` response header:

```json
{
  "requestId": "abc-123",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": { "field": "must not be blank" },
  "timestamp": "2026-07-19T23:00:00Z"
}
```

---

## Future AWS Target

> **Not yet applied.** The Terraform under [`infra/aws`](infra/aws) describes a possible future
> architecture (ECS Fargate, ALB, WAF, multi-AZ VPC). The running deployment is a single EC2
> instance. See [What's Actually Running](#whats-actually-running).

It provisions isolated multi-AZ VPC networking, ALB + WAF at the edge, ECS Fargate for the backend,
private RDS with KMS encryption, Secrets Manager, a Cognito user pool, CloudWatch logs and alarms,
and least-privilege ECS IAM roles. Security design notes live in
[`docs/AWS_SECURITY_ARCHITECTURE.md`](docs/AWS_SECURITY_ARCHITECTURE.md).

---

## Roadmap

1. Complete the Secrets Manager cutover in production (code shipped; awaiting IAM policy attach).
2. Migrate from single-EC2 to the ECS Fargate blueprint for horizontal scaling.
3. Real provider token-exchange clients for GitHub, Jira, and CI (beyond simulated installs).
4. Redis-backed hot-read caching with cache health checks.
5. Worker autoscaling on queue depth.
6. Broaden E2E coverage to signup, MFA enrollment, and incident remediation flows.

---

## Docs

- [DEMO.md](DEMO.md): two-minute demo talk track
- [docs/PRD.md](docs/PRD.md): product requirements
- [docs/AWS_SECURITY_ARCHITECTURE.md](docs/AWS_SECURITY_ARCHITECTURE.md): security design and controls map
- [docs/openapi.yaml](docs/openapi.yaml): full API specification
