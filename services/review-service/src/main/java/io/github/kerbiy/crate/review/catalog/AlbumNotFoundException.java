package io.github.kerbiy.crate.review.catalog;

import java.util.UUID;

/** catalog-service says this album doesn't exist (it answered 404). */
public class AlbumNotFoundException extends RuntimeException {

    public AlbumNotFoundException(UUID albumId) {
        super("Album " + albumId + " doesn't exist");
    }
}
