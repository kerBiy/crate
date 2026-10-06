package io.github.kerbiy.crate.catalog.musicbrainz;

/**
 * MusicBrainz couldn't give us an answer: 503s after every retry, a timeout, a network error,
 * an unexpected status, or too long a wait for the rate limiter. Callers treat all of these alike
 * (search falls back to local results), so there is one exception type.
 */
public class MusicBrainzException extends RuntimeException {

    public MusicBrainzException(String message) {
        super(message);
    }

    public MusicBrainzException(String message, Throwable cause) {
        super(message, cause);
    }
}
