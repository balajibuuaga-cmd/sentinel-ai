# Sentinel AI AWS Infrastructure

Terraform for an AWS-ready Sentinel AI production baseline.

> **Status: not yet applied.** The app currently deployed and live is a single EC2 instance + real RDS Postgres, provisioned manually (see the top-level [README](../../README.md#current-live-deployment) and `scripts/deploy.sh`), using DB-backed auth rather than the Cognito flow described here. This Terraform module is a possible future migration target for scaling beyond a single instance — running `terraform apply` today would create a second, parallel, disconnected set of AWS resources rather than modify what's live.

## What It Creates

- VPC across 2-3 availability zones
- Public subnets for an Application Load Balancer
- Private app subnets for ECS Fargate
- Private data subnets for RDS PostgreSQL
- NAT gateways for controlled outbound app traffic
- ECS cluster, API task/service, and worker task/service
- RDS PostgreSQL with KMS encryption and automated backups
- Secrets Manager secret for DB password, demo JWT secret, and GitHub webhook secret
- Cognito user pool and app client for production customer auth
- Regional WAF attached to the ALB
- CloudWatch log group and core alarms
- Least-privilege ECS task execution/runtime roles

## Prerequisites

1. Build and push the app container image.
2. Create or choose an ACM certificate if you want HTTPS on the ALB.
3. Configure AWS credentials locally or in CI.
4. Create a Terraform backend if you want remote state.

## Example

```bash
cd infra/aws
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

Set `container_image` in `terraform.tfvars` before planning.

For production login, also set `app_base_url` to the public HTTPS origin users will open, and choose a globally unique `cognito_domain_prefix` for the Hosted UI domain.

## Container Runtime

The ECS task maps Sentinel's existing environment variables:

- `PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `SPRING_FLYWAY_ENABLED`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE`
- `SENTINEL_REDIS_RATE_LIMIT_ENABLED`
- `SENTINEL_REDIS_HOST`
- `SENTINEL_REDIS_PORT`
- `SENTINEL_JWT_SECRET`
- `SENTINEL_GITHUB_WEBHOOK_SECRET`
- `SENTINEL_API_ENABLED`
- `SENTINEL_WORKER_ENABLED`
- `SENTINEL_AI_PROVIDER`
- `SENTINEL_AI_MODEL`
- `SENTINEL_AI_EXTERNAL_CALLS_ENABLED`
- `SENTINEL_AUTH_MODE`
- `SENTINEL_COGNITO_ISSUER`
- `SENTINEL_COGNITO_AUDIENCE`
- `SENTINEL_COGNITO_CLIENT_ID`
- `SENTINEL_COGNITO_CLIENT_SECRET`
- `SENTINEL_COGNITO_HOSTED_UI_BASE_URL`
- `SENTINEL_COGNITO_REDIRECT_URI`
- `SENTINEL_COGNITO_LOGOUT_URI`
- `SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED`
- `SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY`
- `SENTINEL_GITHUB_CLIENT_ID`
- `SENTINEL_GITHUB_CLIENT_SECRET`
- `SENTINEL_GITHUB_REDIRECT_URI`
- `SENTINEL_JIRA_CLIENT_ID`
- `SENTINEL_JIRA_CLIENT_SECRET`
- `SENTINEL_JIRA_REDIRECT_URI`
- `SENTINEL_JIRA_CLOUD_ID`
- `SENTINEL_CI_CLIENT_ID`
- `SENTINEL_CI_CLIENT_SECRET`
- `SENTINEL_CI_REDIRECT_URI`
- `SENTINEL_CI_AUTHORIZE_URL`
- `SENTINEL_CI_TOKEN_URL`
- `SENTINEL_CI_RUNS_URL`

Secrets are stored in AWS Secrets Manager and injected into the ECS task at runtime.

The ALB-facing API service runs with `SENTINEL_API_ENABLED=true` and `SENTINEL_WORKER_ENABLED=false`.
The private worker service runs with `SENTINEL_API_ENABLED=false` and `SENTINEL_WORKER_ENABLED=true`, so it processes background jobs, incident follow-ups, provider retries, and webhook replay without serving product API traffic.

## Production Notes

- RDS is private and only accepts traffic from the ECS service security group.
- Deletion protection is enabled by default for the ALB, Cognito, and RDS.
- The app currently serves frontend assets from Spring Boot. A later split can move static assets to S3 + CloudFront.
- ECS runs the app with `SENTINEL_AUTH_MODE=cognito`, so users sign in through Cognito Hosted UI and API requests carry Cognito tokens with `custom:role`, `custom:tenant_id`, and `custom:organization_name` claims.
- ECS scales API and worker tasks independently with `desired_count` and `worker_desired_count`.
- Set `integrations_real_exchange_enabled=true` only after provider OAuth apps are configured with callback URLs under `/integrations/{provider}/callback`.
- WAF uses AWS managed common and known-bad-inputs rule sets plus a rate limit.
- CloudWatch receives structured operation logs with request IDs, tenant IDs, provider names, and sync failure categories.
- Add a remote backend before using this with a real team.
