package io.github.kerbiy.crate.catalog.listenbrainz;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.listenbrainz}.
 *
 * @param baseUrl      the API, e.g. https://api.listenbrainz.org
 * @param timeout      connect and read timeout of the one call per search; past it, search ranks as if
 *                     ListenBrainz didn't exist
 * @param rateLimit    ListenBrainz asks for at most one call per second
 * @param refreshAfter stored listener counts are asked for again after this long
 */
@ConfigurationProperties("crate.listenbrainz")
public record ListenBrainzProperties(URI baseUrl, Duration timeout, RateLimit rateLimit, Duration refreshAfter) {

    /** One call per {@code interval}; a caller that would wait longer than {@code maxWait} goes without. */
    public record RateLimit(Duration interval, Duration maxWait) {
    }
}
