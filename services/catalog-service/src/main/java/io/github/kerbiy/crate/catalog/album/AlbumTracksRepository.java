package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.ListenLink;
import io.github.kerbiy.crate.catalog.musicbrainz.ListenPlatform;
import io.github.kerbiy.crate.catalog.musicbrainz.Release;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chosen release of each album, its tracks and its listening links (album_releases, album_tracks,
 * album_listen_links), in plain SQL.
 */
@Repository
class AlbumTracksRepository {

    private final JdbcClient jdbc;

    AlbumTracksRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The stored tracklist. Empty when there is none yet, or when MusicBrainz had no release
     * {@code recheckEmptyAfter} ago or longer: it may have one by now.
     */
    @Transactional(readOnly = true)
    public Optional<AlbumTracks> find(UUID albumId, Duration recheckEmptyAfter) {
        Optional<Stored> stored = jdbc.sql("""
                        select release_id, label, track_count, length_ms, genres, fetched_at
                        from album_releases where album_id = :id
                        """)
                .param("id", albumId)
                .query((rs, rowNum) -> new Stored(
                        rs.getObject("release_id", UUID.class),
                        rs.getString("label"),
                        rs.getObject("track_count", Integer.class),
                        rs.getObject("length_ms", Long.class),
                        AlbumRepository.texts(rs.getArray("genres")),
                        rs.getObject("fetched_at", OffsetDateTime.class)))
                .optional();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Stored row = stored.get();
        if (row.releaseId() == null) {
            boolean stale = row.fetchedAt().toInstant().isBefore(Instant.now().minus(recheckEmptyAfter));
            return stale ? Optional.empty()
                    : Optional.of(new AlbumTracks(null, null, null, null, List.of(), List.of(), links(albumId)));
        }
        return Optional.of(new AlbumTracks(row.releaseId(), row.label(), row.trackCount(), row.lengthMs(),
                row.genres(), discs(albumId), links(albumId)));
    }

    /** In ListenPlatform order, whatever order they were stored in. */
    private List<ListenLink> links(UUID albumId) {
        return jdbc.sql("select platform, url from album_listen_links where album_id = :id")
                .param("id", albumId)
                .query((rs, rowNum) -> new ListenLink(ListenPlatform.valueOf(rs.getString("platform")), rs.getString("url")))
                .list()
                .stream()
                .sorted(Comparator.comparing(ListenLink::platform))
                .toList();
    }

    private List<AlbumTracks.Disc> discs(UUID albumId) {
        Map<Integer, List<AlbumTracks.Track>> byDisc = new LinkedHashMap<>();
        jdbc.sql("""
                        select disc, position, number, title, length_ms from album_tracks
                        where album_id = :id order by disc, position
                        """)
                .param("id", albumId)
                .query(rs -> {
                    byDisc.computeIfAbsent(rs.getInt("disc"), disc -> new ArrayList<>()).add(new AlbumTracks.Track(
                            rs.getInt("position"), rs.getString("number"), rs.getString("title"),
                            rs.getObject("length_ms", Integer.class)));
                });
        return byDisc.entrySet().stream()
                .map(entry -> new AlbumTracks.Disc(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    /**
     * Replaces the album's release, tracks and listening links in one transaction; {@code release} null
     * records that MusicBrainz has none (an album can still have links then).
     *
     * <p>Two requests storing the same album at once are safe: the upsert locks the album_releases row,
     * so the second transaction waits, then deletes the first one's tracks and writes its own (the same
     * ones). Nobody ever reads a half-written tracklist.
     */
    @Transactional
    public void store(UUID albumId, Release release, List<ListenLink> links) {
        jdbc.sql("""
                        insert into album_releases (album_id, release_id, label, track_count, length_ms, genres, fetched_at)
                        values (:id, :releaseId, :label, :trackCount, :lengthMs, :genres, now())
                        on conflict (album_id) do update set
                            release_id = excluded.release_id,
                            label = excluded.label,
                            track_count = excluded.track_count,
                            length_ms = excluded.length_ms,
                            genres = excluded.genres,
                            fetched_at = excluded.fetched_at
                        """)
                .param("id", albumId)
                .param("releaseId", release == null ? null : release.id())
                .param("label", release == null ? null : release.label())
                .param("trackCount", release == null ? null : release.trackCount())
                .param("lengthMs", release == null ? null : release.lengthMs())
                .param("genres", release == null ? new String[0] : release.genres().toArray(String[]::new))
                .update();
        jdbc.sql("delete from album_tracks where album_id = :id").param("id", albumId).update();
        jdbc.sql("delete from album_listen_links where album_id = :id").param("id", albumId).update();
        for (ListenLink link : links) {
            jdbc.sql("insert into album_listen_links (album_id, platform, url) values (:id, :platform, :url)")
                    .param("id", albumId)
                    .param("platform", link.platform().name())
                    .param("url", link.url())
                    .update();
        }
        if (release == null) {
            return;
        }
        for (Release.Disc disc : release.discs()) {
            for (Release.Track track : disc.tracks()) {
                jdbc.sql("""
                                insert into album_tracks (album_id, disc, position, number, title, length_ms)
                                values (:id, :disc, :position, :number, :title, :lengthMs)
                                """)
                        .param("id", albumId)
                        .param("disc", disc.position())
                        .param("position", track.position())
                        .param("number", track.number())
                        .param("title", track.title())
                        .param("lengthMs", track.lengthMs())
                        .update();
            }
        }
    }

    private record Stored(UUID releaseId, String label, Integer trackCount, Long lengthMs, List<String> genres,
            OffsetDateTime fetchedAt) {
    }
}
