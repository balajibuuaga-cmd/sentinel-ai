alter table users add column reset_token_hash varchar(255);
alter table users add column reset_token_expires_at timestamp with time zone;
