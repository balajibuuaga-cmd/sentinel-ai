-- The install_url column holds each integration's OAuth authorize URL. Jira's
-- authorize URL (auth.atlassian.com with the audience param, url-encoded scopes,
-- and the encoded redirect_uri) runs ~325 characters, overflowing the original
-- varchar(255) and failing every insert with SQLSTATE 22001. GitHub's shorter
-- URL fit, which is why this only surfaced when the Jira integration went live.
-- Widen to 2048 so any provider's authorize URL fits with room to spare.
alter table integration_connections alter column install_url type varchar(2048);
