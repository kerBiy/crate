package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.List;
import java.util.UUID;

/**
 * The release the album page reads its facts and tracklist from.
 *
 * @param label  the first label credited, or null
 * @param discs  the media in order; most albums have one
 * @param genres the release group's genres (not the release's: the group gathers far more votes),
 *               most votes first, at most {@link #MAX_GENRES}
 */
public record Release(UUID id, String label, List<Disc> discs, List<String> genres) {

    public static final int MAX_GENRES = 5;

    public Release {
        discs = List.copyOf(discs);
        genres = List.copyOf(genres);
    }

    public int trackCount() {
        return discs.stream().mapToInt(disc -> disc.tracks().size()).sum();
    }

    /** Total length in milliseconds, or null if any track has no length (a sum would be too short). */
    public Long lengthMs() {
        List<Track> tracks = discs.stream().flatMap(disc -> disc.tracks().stream()).toList();
        if (tracks.isEmpty() || tracks.stream().anyMatch(track -> track.lengthMs() == null)) {
            return null;
        }
        return tracks.stream().mapToLong(Track::lengthMs).sum();
    }

    /** One medium (CD, side pair of a vinyl, ...), {@code position} 1-based. */
    public record Disc(int position, List<Track> tracks) {

        public Disc {
            tracks = List.copyOf(tracks);
        }
    }

    /** {@code position} 1-based within the disc; {@code number} as printed ("A1"); {@code lengthMs} null when unknown. */
    public record Track(int position, String number, String title, Integer lengthMs) {
    }
}
