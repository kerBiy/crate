-- Where to listen (docs/adr/015-where-to-listen.md): links that open the album on Spotify, Apple Music,
-- YouTube Music, Deezer…, one per platform, read from the releases' url-rels in the same browse that
-- picks the tracklist's release (ADR 013), and stored with it.
create table album_listen_links (
  album_id uuid        not null references album_releases on delete cascade,
  platform varchar(20) not null,               -- ListenPlatform: SPOTIFY, APPLE_MUSIC, YOUTUBE_MUSIC…
  url      text        not null,
  primary key (album_id, platform)
);

-- album_releases is a cache of MusicBrainz. Rows stored before V10 were fetched without links, so empty
-- it: each album fetches its tracklist and links again on its next view (the same two requests as a
-- first view). The tracks go with it (on delete cascade).
delete from album_releases;
