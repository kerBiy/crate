package io.github.kerbiy.crate.catalog.listenbrainz;

/**
 * ListenBrainz gave no usable answer: a timeout, a network error, a non-2xx status (429 included) or
 * a full rate-limit queue. Search then ranks without listener counts, so one exception type is enough.
 */
public class ListenBrainzException extends RuntimeException {

    public ListenBrainzException(String message) {
        super(message);
    }

    public ListenBrainzException(String message, Throwable cause) {
        super(message, cause);
    }
}
