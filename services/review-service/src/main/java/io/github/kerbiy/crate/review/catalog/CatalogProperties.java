package io.github.kerbiy.crate.review.catalog;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code crate.catalog}. */
@ConfigurationProperties("crate.catalog")
public record CatalogProperties(URI baseUrl, Duration timeout) {

    public CatalogProperties {
        // Fail at startup, not on the first rating.
        if (baseUrl == null) {
            throw new IllegalArgumentException("crate.catalog.base-url must be set");
        }
        if (timeout == null) {
            throw new IllegalArgumentException("crate.catalog.timeout must be set");
        }
    }
}
