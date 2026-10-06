package io.github.kerbiy.crate.catalog.album;

import java.util.UUID;

/** An album in a list: search results and the batch endpoint. */
public record AlbumSummary(
        UUID id,
        String title,
        String artistCredit,
        String year,
        String coverUrl,
        Double avgRating,
        int ratingCount) {

    public static AlbumSummary from(AlbumRow album) {
        return new AlbumSummary(album.id(), album.title(), album.artistCredit(), album.year(),
                CoverArt.front250(album.id()), album.avgRating(), album.ratingCount());
    }
}
