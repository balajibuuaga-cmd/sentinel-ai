-- Weak-point hardening #1: referential integrity was mostly app-enforced. Only 7
-- of the model's relationships had database foreign keys; these four *_id columns
-- pointed at a parent row with nothing stopping an orphan. Add the missing keys
-- so the database enforces the relationship, matching the existing convention
-- (no ON DELETE clause -> NO ACTION; the app never hard-deletes these parents,
-- so a restrict never fires — disconnect is a soft status change, not a delete).
--
-- Verified against production before writing this: zero orphan rows in all four,
-- so the constraints apply cleanly. background_jobs.target_id is deliberately
-- left unconstrained — it is a polymorphic reference paired with target_type and
-- cannot point at a single table.

alter table incidents
    add constraint fk_incidents_deployment
    foreign key (deployment_id) references deployments(id);

alter table integration_sync_events
    add constraint fk_integration_sync_events_connection
    foreign key (integration_connection_id) references integration_connections(id);

alter table memory_links
    add constraint fk_memory_links_deployment
    foreign key (deployment_id) references deployments(id);

alter table pull_request_reviews
    add constraint fk_pull_request_reviews_deployment
    foreign key (linked_deployment_id) references deployments(id);
