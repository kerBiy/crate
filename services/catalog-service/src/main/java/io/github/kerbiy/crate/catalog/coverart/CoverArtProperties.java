package io.github.kerbiy.crate.catalog.coverart;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.catalog.cover-art}.
 *
 * @param baseUrl      Cover Art Archive, e.g. https://coverartarchive.org
 * @param concurrency  most checks in flight at once
 * @param timeout      per check; a check that takes longer counts as "don't know"
 * @param deadline     for a whole batch; checks still running then are abandoned
 * @param recheckAfter "no cover" is asked again after this long: people upload covers later
 */
@ConfigurationProperties("crate.catalog.cover-art")
public record CoverArtProperties(URI baseUrl, int concurrency, Duration timeout, Duration deadline, Duration recheckAfter) {
}
