# Sentinel AI Product Requirements

## One-Line Pitch

Sentinel AI helps engineering teams know which deployments are risky before they reach production.

## Target Customer

Initial customer:

- SaaS engineering teams with frequent deployments
- Platform engineering teams
- SRE teams
- Release managers

Primary buyer:

- VP Engineering
- CTO
- Head of Platform
- SRE Manager

## MVP Problem

Engineering teams often deploy with disconnected signals:

- Code changed in GitHub
- Jira tickets changed scope
- CI tests failed or coverage dropped
- Logs show existing runtime instability
- Similar incidents happened before
- Services have hidden dependencies

The team needs one release-risk decision before production impact.

## MVP User Story

As a release manager, I want Sentinel AI to evaluate a deployment and explain risk, so I can approve, delay, or block the release with confidence.

## MVP Scope

In scope:

- View deployments
- View risk score
- View evidence and recommendation
- View service dependencies
- Approve/block/request changes
- Record audit events
- Show AWS security control map

Out of scope for first version:

- Real GitHub OAuth
- Real Jira OAuth
- Real CI vendor integration
- Real LLM calls
- Multi-tenant billing
- Kubernetes deployment

## Risk Scoring Inputs

- Production environment
- Code change surface area
- Failed tests
- Coverage drop
- Database migrations
- High-priority Jira tickets
- Runtime log anomalies
- Similar past incidents
- Number of dependent services

## Release Decision Rules

- 0-34: Low risk, normal deployment.
- 35-64: Medium risk, require targeted checks.
- 65-84: High risk, require senior approval.
- 85-100: Critical risk, block until resolved.

## Success Metrics

- User understands risk in less than 30 seconds.
- User can approve or block in one click.
- Every decision has an audit record.
- Demo clearly answers, "Why is this deployment risky?"

## Company Differentiation

Sentinel AI is not another monitoring dashboard. It is a pre-production decision engine that correlates engineering signals before incidents happen.
