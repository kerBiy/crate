-- Search ranking (docs/adr/010-search-ranking.md).

-- Popularity signal and release type, from MusicBrainz search results.
-- Null = unknown: rows stored before V3, and release_count after a lookup by id (MusicBrainz
-- reports the count only in search results).
alter table albums
  add column release_count   integer,
  add column secondary_types text[];

-- The ranked answer MusicBrainz gave for a query, so a cached query replays that order instead of
-- re-ranking by trigrams. Rows go when the query's cache entry or the album goes.
create table search_results (
  normalized_query text    not null references search_cache (normalized_query) on delete cascade,
  album_id         uuid    not null references albums (id) on delete cascade,
  rank             integer not null,   -- 1 = best
  primary key (normalized_query, album_id)
);

-- Every cached query was ranked the old way and has no search_results: ask MusicBrainz again.
-- Only a cache; albums are left alone.
delete from search_cache;
