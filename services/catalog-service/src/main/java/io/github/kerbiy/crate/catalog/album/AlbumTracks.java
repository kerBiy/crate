package io.github.kerbiy.crate.catalog.album;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /albums/{id}/tracks}: the tracklist of the album's chosen release (ADR 013), with the facts
 * that come from that release. {@code discs} is empty (and the rest null) when MusicBrainz has no release
 * with tracks for the album.
 *
 * @param lengthMs null when a track has no length
 * @param genres   the release group's genres, most votes first
 */
record AlbumTracks(UUID releaseId, String label, Integer trackCount, Long lengthMs, List<String> genres,
        List<Disc> discs) {

    record Disc(int position, List<Track> tracks) {
    }

    /** {@code number} as printed ("A1"); {@code lengthMs} null when unknown. */
    record Track(int position, String number, String title, Integer lengthMs) {
    }
}
