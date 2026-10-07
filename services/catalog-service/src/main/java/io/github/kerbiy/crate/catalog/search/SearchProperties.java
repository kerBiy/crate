package io.github.kerbiy.crate.catalog.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.catalog.search}.
 *
 * @param matchThreshold   minimum pg_trgm word similarity (0–1) for an album to match locally; only
 *                         used when MusicBrainz can't be reached
 * @param cacheTtl         how long a query MusicBrainz already answered is served from our DB
 * @param musicBrainzLimit how many release groups to request per MusicBrainz search (max 100)
 * @param popularityWeight how much the number of releases counts next to MusicBrainz's score
 *                         (see {@link SearchRanking#merge})
 */
@ConfigurationProperties("crate.catalog.search")
record SearchProperties(double matchThreshold, Duration cacheTtl, int musicBrainzLimit, double popularityWeight) {
}
