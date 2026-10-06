package io.github.kerbiy.crate.catalog.musicbrainz;

/** Gate in front of every request to MusicBrainz, retries included. */
@FunctionalInterface
public interface RateLimiter {

    /** Blocks until one request may be sent; throws {@link MusicBrainzException} if that takes too long. */
    void acquire();
}
