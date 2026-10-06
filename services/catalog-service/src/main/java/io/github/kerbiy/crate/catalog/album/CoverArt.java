package io.github.kerbiy.crate.catalog.album;

import java.util.UUID;

/**
 * Cover images are never stored: the Cover Art Archive serves them by release-group MBID.
 * The URL may 404 (no cover uploaded); the frontend shows a placeholder then.
 */
final class CoverArt {

    private static final String BASE = "https://coverartarchive.org/release-group/";

    private CoverArt() {
    }

    /** Thumbnail for lists (search results, feeds). */
    static String front250(UUID albumId) {
        return BASE + albumId + "/front-250";
    }

    /** Larger image for the album page. */
    static String front500(UUID albumId) {
        return BASE + albumId + "/front-500";
    }
}
