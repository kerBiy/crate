package io.github.kerbiy.crate.catalog.album;

import java.util.UUID;

/** An album as stored in catalog_db, with its rating aggregate (zeros when nobody rated it yet). */
public record AlbumRow(
        UUID id,
        String title,
        String artistCredit,
        UUID primaryArtistId,
        String primaryType,
        String firstReleaseDate,
        int ratingCount,
        int ratingSum) {

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
