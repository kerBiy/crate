package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.ListenLink;
import io.github.kerbiy.crate.catalog.musicbrainz.ListenLinkChoice;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.Release;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseChoice;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseSummary;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The tracklist, as a read-through cache: fetched from MusicBrainz the first time someone opens it
 * (two requests: the group's releases, then the chosen one, ADR 013), then served from our DB. The
 * listening links (ADR 015) come from the same browse of the group's releases, at no extra request.
 * Like {@link AlbumService}, no {@code @Transactional}: MusicBrainz is never called inside a transaction.
 */
@Service
class AlbumTracksService {

    private static final Logger log = LoggerFactory.getLogger(AlbumTracksService.class);

    private final AlbumService albums;
    private final AlbumTracksRepository tracks;
    private final MusicBrainzClient musicBrainz;
    private final AlbumProperties properties;

    AlbumTracksService(AlbumService albums, AlbumTracksRepository tracks, MusicBrainzClient musicBrainz,
            AlbumProperties properties) {
        this.albums = albums;
        this.tracks = tracks;
        this.musicBrainz = musicBrainz;
        this.properties = properties;
    }

    /** 404 for an album MusicBrainz doesn't know; 503 when MusicBrainz can't be reached for the tracklist. */
    AlbumTracks get(UUID id) {
        // By the stored id: for a merged MBID it is the surviving release group's.
        UUID albumId = albums.get(id).id();
        return tracks.find(albumId, properties.recheckEmptyTracksAfter())
                .orElseGet(() -> fetchAndStore(albumId));
    }

    private AlbumTracks fetchAndStore(UUID albumId) {
        Optional<Release> release;
        List<ListenLink> links;
        try {
            List<ReleaseSummary> releases = musicBrainz.releases(albumId);
            Optional<ReleaseSummary> choice = ReleaseChoice.choose(releases);
            links = ListenLinkChoice.choose(releases, choice.orElse(null));
            release = choice.flatMap(chosen -> musicBrainz.release(chosen.id()));
        } catch (MusicBrainzException e) {
            // Nothing stored, so the next request tries again. The rest of the album page is unaffected.
            log.warn("MusicBrainz tracklist of {} failed: {}", albumId, e.getMessage());
            throw Problems.exception(HttpStatus.SERVICE_UNAVAILABLE, "tracks-unavailable",
                    "Tracklist unavailable", "Couldn't load the tracklist from MusicBrainz. Try again later.");
        }
        tracks.store(albumId, release.orElse(null), links);
        return tracks.find(albumId, properties.recheckEmptyTracksAfter()).orElseThrow();
    }
}
