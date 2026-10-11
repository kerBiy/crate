-- Popularity from ListenBrainz (docs/adr/012-listenbrainz-popularity.md).
-- listener_count: ListenBrainz users who listened to the album; 0 also when ListenBrainz doesn't know it.
-- listeners_checked_at: when it was fetched; null = never. Fetched again after 30 days.
alter table albums
  add column listener_count       integer,
  add column listeners_checked_at timestamptz;

-- Cached rankings were made without listener counts: ask MusicBrainz again (only a cache;
-- search_results go with it via the foreign key).
delete from search_cache;
