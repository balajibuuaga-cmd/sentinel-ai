-- Weak-point hardening #3: free-form columns were sized with guessed varchar
-- limits (varchar(800), (1000), (1600), ...). Provider payloads, AI output, and
-- user-entered text have no natural bound, so every guess is a latent SQLSTATE
-- 22001 waiting for a longer-than-expected value — exactly the class of bug that
-- broke Jira connect (install_url) and GitHub webhooks (payload). Convert every
-- free-form column (anything previously sized beyond the 255 default) to text.
-- Bounded identifiers, enums, keys, names, and SHAs stay varchar on purpose.
--
-- webhook_deliveries.payload is already text (V10) and is intentionally omitted.

alter table architecture_dependencies    alter column notes              type text;
alter table architecture_risks           alter column explanation        type text;
alter table architecture_risks           alter column recommendation     type text;
alter table architecture_services        alter column description        type text;
alter table audit_events                 alter column details            type text;
alter table background_jobs              alter column last_error         type text;
alter table background_jobs              alter column payload            type text;
alter table deployment_risk_reasons      alter column evidence           type text;
alter table deployment_signals           alter column description        type text;
alter table deployments                  alter column ai_explanation     type text;
alter table deployments                  alter column pull_request_title type text;
alter table deployments                  alter column recommendation     type text;
alter table engineering_events           alter column details            type text;
alter table error_events                 alter column message            type text;
alter table error_events                 alter column path               type text;
alter table incident_timeline_events     alter column detail             type text;
alter table incidents                    alter column affected_systems   type text;
alter table incidents                    alter column commander_brief    type text;
alter table incidents                    alter column recommended_action type text;
alter table incidents                    alter column summary            type text;
alter table integration_connections      alter column status_detail      type text;
alter table integration_sync_events      alter column detail             type text;
alter table integration_token_secrets    alter column encrypted_access_token  type text;
alter table integration_token_secrets    alter column encrypted_refresh_token type text;
alter table memory_links                 alter column reason             type text;
alter table pull_request_reviews         alter column decision_note      type text;
alter table pull_request_reviews         alter column explanation        type text;
alter table pull_request_reviews         alter column title              type text;
alter table webhook_deliveries           alter column failure_reason     type text;
