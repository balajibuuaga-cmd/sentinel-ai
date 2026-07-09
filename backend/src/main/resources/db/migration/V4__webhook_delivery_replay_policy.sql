alter table webhook_deliveries add column next_replay_at timestamp with time zone;
alter table webhook_deliveries add column max_replay_attempts integer not null default 3;
alter table webhook_deliveries add column expires_at timestamp with time zone;

create index idx_webhook_deliveries_replay_eligibility
    on webhook_deliveries(tenant_id, status, next_replay_at, expires_at);

create index idx_webhook_deliveries_retention
    on webhook_deliveries(status, expires_at);
