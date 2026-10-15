package io.github.kerbiy.crate.catalog.wikipedia;

/**
 * Wikidata or Wikipedia gave no usable answer: a timeout, a network error, a non-2xx status other than
 * 404 (429 included), unreadable JSON or a full rate-limit queue. The album page then simply has no
 * description, and the next view asks again.
 */
public class WikipediaException extends RuntimeException {

    public WikipediaException(String message) {
        super(message);
    }

    public WikipediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
