# Database hardening decisions

A deliberate pass over the schema's weak points. Recorded here so the outcomes —
especially the one we chose *not* to do — read as decisions, not oversights.

## #3 — Free-form `varchar` → `text` (done, V12)

Free-form columns (AI output, provider payloads, incident briefs, user text) were
sized with guessed `varchar` limits. Each guess was a latent `SQLSTATE 22001` for
a longer-than-expected value — the bug that broke Jira connect (`install_url`) and
GitHub webhook ingestion (`payload`). Every column previously sized beyond the
`varchar(255)` default is now `text`. Bounded identifiers, enums, keys, names, and
SHAs stay `varchar` on purpose. No entity changes were needed — Hibernate
`validate` compares JDBC type codes and both `varchar` and `text` resolve to
`VARCHAR`, verified by booting against a real PostgreSQL container.

## #1 — Missing foreign keys (done, V13)

Only 7 of the model's relationships had database FKs. Added four more
(`incidents.deployment_id`, `integration_sync_events.integration_connection_id`,
`memory_links.deployment_id`, `pull_request_reviews.linked_deployment_id`) after
verifying zero orphan rows in production. `background_jobs.target_id` stays
unconstrained on purpose — it is a polymorphic reference paired with
`target_type` and cannot point at a single table.

## #4 — Enum CHECK constraints (won't do — deliberate)

**Decision: not adding CHECK constraints to the 20 `@Enumerated(EnumType.STRING)`
columns.** The application writes these columns only through Hibernate, which
serializes a Java enum's `name()` — always a valid constant. The app therefore
*cannot* write an invalid value; the only way a bad value lands is an out-of-band
SQL write. A CHECK constraint would guard that narrow case while imposing real
standing cost: every new enum value (a new incident status, provider, remediation
step) would need a paired migration to widen the constraint, or production inserts
would start failing. The `@Enumerated` mapping is the enforcement. Poor
cost/benefit; skipped intentionally.

## #2 — Tenant normalization (in progress)

`tenant_id` is a bare `varchar` with no canonical registry, and
`organization_name` is denormalized across ~18 tables. See the migration and
entity changes for the scope actually taken.
