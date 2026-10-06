package io.github.kerbiy.crate.catalog.musicbrainz;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code crate.musicbrainz}. */
@ConfigurationProperties("crate.musicbrainz")
public record MusicBrainzProperties(
        URI baseUrl,
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        RateLimit rateLimit,
        Retry retry) {

    public MusicBrainzProperties {
        // MusicBrainz blocks clients without a meaningful User-Agent, so fail at startup, not on the first search.
        if (userAgent == null || userAgent.isBlank() || userAgent.contains("${")) {
            throw new IllegalArgumentException("crate.musicbrainz.user-agent must be set (MUSICBRAINZ_CONTACT)");
        }
    }

    /** One request per {@code interval}; a caller that would wait longer than {@code maxWait} gives up. */
    public record RateLimit(Duration interval, Duration maxWait) {
    }

    /** {@code maxAttempts} counts the first try too: 3 means at most 2 retries. */
    public record Retry(int maxAttempts, Duration initialBackoff) {
    }
}
