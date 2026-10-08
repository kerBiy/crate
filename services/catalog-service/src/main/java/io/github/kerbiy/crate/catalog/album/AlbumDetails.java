package io.github.kerbiy.crate.catalog.album;

import java.util.List;
import java.util.UUID;

/**
 * Everything the album page needs. {@code ratingDistribution}: how many ratings each half-star value
 * got, 10 entries, ½ star first and 5 stars last (the histogram).
 */
record AlbumDetails(
        UUID id,
        String title,
        String artistCredit,
        UUID primaryArtistId,
        String primaryType,
        String firstReleaseDate,
        String year,
        String coverUrl,
        Double avgRating,
        int ratingCount,
        List<Integer> ratingDistribution) {

    static AlbumDetails from(AlbumRow album) {
        return new AlbumDetails(album.id(), album.title(), album.artistCredit(), album.primaryArtistId(),
                album.primaryType(), album.firstReleaseDate(), album.year(), CoverArt.front500(album.id()),
                album.avgRating(), album.ratingCount(), album.ratingDistribution());
    }
}
