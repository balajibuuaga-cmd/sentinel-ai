-- GitHub's real webhook payloads run to tens of kilobytes: a pull_request event
-- carries the full repository, pull request, and sender objects. The column was
-- sized varchar(6000) against Sentinel's own hand-rolled payload shape, so every
-- delivery from a real repository failed on insert with SQLSTATE 22001 before
-- the event was ever processed.
--
-- The stored payload is what the delivery replay feature re-sends, so truncating
-- it would leave replays sending a body that no longer verifies against its
-- signature. Widen instead.
alter table webhook_deliveries alter column payload type text;
