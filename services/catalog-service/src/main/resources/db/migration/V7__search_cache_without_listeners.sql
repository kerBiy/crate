-- A ranking made while ListenBrainz couldn't be asked (ADR-012) is kept for 24 hours instead of 7 days,
-- so an outage doesn't freeze rankings without listener counts for a week. Existing rows are normal ones.
alter table search_cache
  add column without_listeners boolean not null default false;
