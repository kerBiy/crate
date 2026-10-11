package io.github.kerbiy.crate.catalog.album;

/** An album in a stored search ranking, with its rank there (1 = best). */
public record RankedAlbum(AlbumRow album, int rank) {
}
