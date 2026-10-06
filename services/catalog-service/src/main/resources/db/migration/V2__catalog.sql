-- Catalog tables (docs/SPEC.md section 5.3). processed_events arrives with the review.events consumer.

-- Trusted extension: catalog_service owns catalog_db, so it may create it without superuser.
create extension if not exists pg_trgm;

create table artists (
  id        uuid primary key,          -- MusicBrainz artist MBID
  name      text not null,
  sort_name text
);

create table albums (
  id                 uuid primary key,  -- MusicBrainz release-group MBID
  title              text not null,
  artist_credit      text not null,     -- display string, e.g. "Simon & Garfunkel"
  primary_artist_id  uuid references artists(id),
  primary_type       varchar(20),       -- Album | EP
  first_release_date varchar(10),       -- MB dates can be partial: "1997" or "1997-05"
  search_text        text not null,     -- normalized in Java: lowercase, no diacritics, title + artist
  fetched_at         timestamptz not null
);
-- Trigram index: answers `query <% search_text` (word similarity) without scanning every row.
create index albums_search_trgm on albums using gin (search_text gin_trgm_ops);

-- No FK to albums on purpose: events may arrive before the album row exists.
create table album_stats (
  album_id     uuid primary key,
  rating_count integer not null default 0,
  rating_sum   integer not null default 0     -- sum of 1–10 values
);

-- Queries we already asked MusicBrainz about; younger than the TTL means "don't ask again".
create table search_cache (
  normalized_query text primary key,
  fetched_at       timestamptz not null
);
