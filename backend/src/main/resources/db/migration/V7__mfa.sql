alter table users add column mfa_enabled boolean not null default false;
alter table users add column mfa_secret varchar(64);
alter table users add column pending_mfa_secret varchar(64);
