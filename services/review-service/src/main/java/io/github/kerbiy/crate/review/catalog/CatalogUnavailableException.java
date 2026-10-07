package io.github.kerbiy.crate.review.catalog;

/** catalog-service couldn't tell whether the album exists: an error status, a timeout, or no connection. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(Throwable cause) {
        super("catalog-service couldn't be asked", cause);
    }
}
