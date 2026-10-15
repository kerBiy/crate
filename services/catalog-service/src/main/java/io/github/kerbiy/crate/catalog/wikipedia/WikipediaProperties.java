package io.github.kerbiy.crate.catalog.wikipedia;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.wikipedia} (ADR 014).
 *
 * @param wikidataBaseUrl  e.g. https://www.wikidata.org: the Action API at /w/api.php
 * @param wikipediaBaseUrl e.g. https://en.wikipedia.org: the REST API at /api/rest_v1
 * @param timeout          connect and read timeout of each call
 * @param rateLimit        our own pace, shared by Wikidata and Wikipedia calls (one Wikimedia budget)
 */
@ConfigurationProperties("crate.wikipedia")
public record WikipediaProperties(URI wikidataBaseUrl, URI wikipediaBaseUrl, Duration timeout, RateLimit rateLimit) {

    /** One call per {@code interval}; a caller that would wait longer than {@code maxWait} goes without. */
    public record RateLimit(Duration interval, Duration maxWait) {
    }
}
