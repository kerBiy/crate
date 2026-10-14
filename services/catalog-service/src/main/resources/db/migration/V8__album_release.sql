-- Album page details (docs/adr/013-release-for-album-details.md).

-- ListenBrainz total listens next to listener_count (ADR 012). null = not fetched since V8: the album
-- page fetches it again, in the background.
alter table albums add column listen_count bigint;

-- The one release the album page reads its length, label and tracklist from (ADR 013), fetched the
-- first time someone asks for the tracklist. Genres are the release group's, most votes first.
create table album_releases (
  album_id    uuid primary key references albums on delete cascade,
  release_id  uuid,                           -- null: MusicBrainz has no release with tracks; asked again after 30 days
  label       text,
  track_count integer,
  length_ms   bigint,                         -- null when a track has no length
  genres      text[] not null default '{}',
  fetched_at  timestamptz not null
);

create table album_tracks (
  album_id  uuid     not null references album_releases on delete cascade,
  disc      smallint not null,                -- medium position, 1-based
  position  smallint not null,                -- within the disc, 1-based
  number    text,                             -- as printed on the sleeve: "1", "A1"
  title     text     not null,
  length_ms integer,
  primary key (album_id, disc, position)
);
