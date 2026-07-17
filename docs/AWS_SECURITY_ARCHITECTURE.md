# AWS Security Architecture

## Goal

Build Sentinel AI as a secure-by-design SaaS platform for deployment risk intelligence.

No application can be guaranteed hacker-free. The target is:

- least privilege
- encrypted data
- audited actions
- monitored infrastructure
- fast detection and recovery
- secure secret handling
- request correlation and operational metrics

## Recommended AWS Services

| Area | AWS Services | Why |
|---|---|---|
| Identity | Cognito, IAM Identity Center, IAM | App login, admin access, least privilege |
| Secrets | Secrets Manager | GitHub/Jira/DB/AI provider secrets |
| Encryption | KMS | Encrypt database, logs, files, secrets |
| Compute | ECS Fargate | Container runtime without server management |
| Database | RDS PostgreSQL | Durable relational product data |
| Frontend | S3, CloudFront | Secure static app delivery |
| Edge Protection | WAF | Web exploit and bot protection |
| Logs | CloudWatch | App logs, metrics, alarms |
| AWS Audit | CloudTrail | AWS account activity trail |
| Threat Detection | GuardDuty | Suspicious AWS activity detection |
| Vulnerabilities | Inspector | Container and dependency scanning |
| Posture | Security Hub | Centralized security findings |
| Recovery | AWS Backup | Database backup and restore |

## Target Production Flow

```text
User
  -> CloudFront
  -> AWS WAF
  -> React Frontend
  -> Cognito RS256 JWT
  -> ALB
  -> ECS Fargate Spring Boot API
  -> Secrets Manager
  -> RDS PostgreSQL encrypted with KMS

Detection and audit:
  CloudWatch + CloudTrail + GuardDuty + Inspector + Security Hub
```

## Security Rules

1. Do not expose RDS publicly.
2. Store no secrets in code or environment files committed to Git.
3. Use IAM roles instead of long-lived AWS keys.
4. Encrypt all sensitive data at rest.
5. Use HTTPS-only traffic.
6. Require MFA for AWS administrators.
7. Apply WAF rate limits to API and login paths.
8. Log every approval, block, and risk recommendation.
9. Back up the database automatically.
10. Scan containers and dependencies before deployment.

## App-Level Controls

- Role-based access: Admin, Release Manager, Viewer.
- Audit trail for every risk score and approval.
- Input validation for approval requests.
- Tenant isolation by organization ID.
- HMAC SHA-256 verification for public GitHub webhook ingestion.
- Demo JWT for local development and Cognito JWT validation for production mode.
- Cognito validation checks RS256 signatures, issuer, audience, expiry, token use, role claims, and tenant claims.
- Request correlation uses `X-Request-ID` across API responses, audit details, and operational logs.
- API errors use a safe JSON contract with request ID, machine-readable code, user-safe message, optional details, and timestamp.
- Provider sync failures are categorized as auth, scope, rate limit, provider outage, bad config, or unknown failures.
- Micrometer metrics count deployment reviews, webhooks, approval decisions, and provider sync outcomes.

## Secrets Loading (implemented)

Application secrets are loaded from AWS Secrets Manager at boot rather than sitting
in plaintext on the host. `SecretsManagerEnvironmentPostProcessor` runs before the
Spring context starts and, when `SENTINEL_SECRETS_ID` is set, fetches a flat JSON
secret whose keys are the existing environment-variable names
(`SENTINEL_JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`, `SENTINEL_GITHUB_WEBHOOK_SECRET`,
`SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY`, Cognito/GitHub/Jira client secrets, …)
and layers them in as the highest-precedence property source. Existing
`${SENTINEL_JWT_SECRET:...}` placeholders resolve straight from it, so no property
changes were required.

Key properties of the mechanism:

- **Opt-in / offline-safe.** With `SENTINEL_SECRETS_ID` unset (local dev, CI, tests)
  the post-processor is a complete no-op — nothing calls AWS.
- **Fail-fast in production.** When the flag IS set but the secret can't be fetched
  or parsed, startup aborts instead of silently falling back to the committed
  `local-dev-*` defaults.
- **Least privilege.** The EC2 instance role is granted only
  `secretsmanager:GetSecretValue` on the single secret ARN.
- Provisioning helper: `scripts/setup-secrets.sh` creates/updates the secret from a
  local (uncommitted) `KEY=VALUE` file and prints the required IAM policy and
  systemd `EnvironmentFile` changes.

## AWS-Ready Runtime Mapping

| Local Capability | AWS Production Target |
|---|---|
| PostgreSQL via Docker Compose | Private RDS PostgreSQL with KMS encryption and automated backups |
| Demo JWT auth mode | Cognito JWT validation using the user pool JWKS |
| `SENTINEL_GITHUB_WEBHOOK_SECRET` | Secrets Manager secret mounted into ECS task |
| Spring Boot container | ECS Fargate service behind an HTTPS ALB |
| Static assets served by Spring Boot | S3 + CloudFront once the frontend is split out |
| Application audit events | RDS audit tables plus CloudWatch log retention |

## Terraform Baseline

The implementation scaffold lives in `infra/aws` and provisions:

- isolated VPC networking across multiple availability zones
- ALB + WAF at the edge
- ECS Fargate for the Spring Boot container
- private RDS PostgreSQL with KMS encryption and backups
- Secrets Manager for application secrets
- Cognito user pool and app client wired into ECS auth environment variables
- CloudWatch logs and alarms
- least-privilege ECS IAM roles

Use `infra/aws/terraform.tfvars.example` as the starting point for account-specific values.
