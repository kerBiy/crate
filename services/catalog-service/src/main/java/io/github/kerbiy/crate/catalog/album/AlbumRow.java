package io.github.kerbiy.crate.catalog.album;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An album as stored in catalog_db, with its rating aggregate (zeros when nobody rated it yet), what
 * ListenBrainz said about it, and the facts of its chosen release (ADR 013) once the tracklist was fetched.
 *
 * @param listenerCount      ListenBrainz listeners; null = never fetched, 0 also = unknown to ListenBrainz
 * @param listenCount        ListenBrainz total listens; null = not fetched since listens were stored
 * @param listenersCheckedAt when ListenBrainz was last asked; null = never
 * @param releaseId          the chosen release; null before the tracklist was fetched, or when MusicBrainz
 *                           has no release with tracks
 * @param genres             the release group's genres, most votes first; empty until the tracklist was fetched
 */
public record AlbumRow(
        UUID id,
        String title,
        String artistCredit,
        UUID primaryArtistId,
        String primaryType,
        String firstReleaseDate,
        int ratingCount,
        int ratingSum,
        List<Integer> ratingDistribution,
        Integer listenerCount,
        Long listenCount,
        Instant listenersCheckedAt,
        UUID releaseId,
        String label,
        Integer trackCount,
        Long lengthMs,
        List<String> genres) {

    /** The distribution of an album nobody rated. */
    public static final List<Integer> NO_RATINGS = Collections.nCopies(10, 0);

    /** "1997" from "1997-05-21", or null when MusicBrainz has no date. */
    public String year() {
        return firstReleaseDate == null || firstReleaseDate.length() < 4 ? null : firstReleaseDate.substring(0, 4);
    }

    /**
     * Average in stars (0.5–5), one decimal. Ratings are stored as 1–10 half-star steps, hence the
     * division by 2. Null when there are no ratings: "no average" is not the same as 0 stars.
     */
    public Double avgRating() {
        if (ratingCount == 0) {
            return null;
        }
        return Math.round((double) ratingSum / ratingCount / 2 * 10) / 10.0;
    }
}
