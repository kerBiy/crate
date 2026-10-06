package io.github.kerbiy.crate.catalog.album;

import java.util.UUID;

/** Everything the album page needs. */
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
        int ratingCount) {

    static AlbumDetails from(AlbumRow album) {
        return new AlbumDetails(album.id(), album.title(), album.artistCredit(), album.primaryArtistId(),
                album.primaryType(), album.firstReleaseDate(), album.year(), CoverArt.front500(album.id()),
                album.avgRating(), album.ratingCount());
    }
}
