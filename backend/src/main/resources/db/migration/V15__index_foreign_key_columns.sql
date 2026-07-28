-- Weak-point hardening follow-on: Postgres auto-indexes primary keys but not the
-- referencing side of a foreign key. Six FK columns had no index, so lookups by
-- them and the FK integrity checks on their parent both fell back to sequential
-- scans. Index each one. The child-collection tables are already covered by a
-- composite primary key that leads with the FK column, so they are not repeated
-- here.

-- Real, frequent query: users are listed per tenant, ordered by creation
-- (findByTenantIdOrderByCreatedAtAsc). A composite index serves the filter and
-- the sort together, matching the existing idx_*_tenant_created_at convention.
create index idx_users_tenant_created_at on users(tenant_id, created_at);

create index idx_incidents_deployment on incidents(deployment_id);
create index idx_integration_sync_events_connection on integration_sync_events(integration_connection_id);
create index idx_memory_links_deployment_fk on memory_links(deployment_id);
create index idx_memory_links_engineering_event on memory_links(engineering_event_id);
create index idx_pull_request_reviews_deployment on pull_request_reviews(linked_deployment_id);
