package io.github.kerbiy.crate.catalog.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.catalog.search}.
 *
 * @param matchThreshold   minimum pg_trgm word similarity (0–1) for an album to count as a match
 * @param goodMatches      this many local matches and we don't ask MusicBrainz
 * @param cacheTtl         how long a query MusicBrainz already answered isn't asked again
 * @param musicBrainzLimit how many release groups to request from MusicBrainz on a miss
 */
@ConfigurationProperties("crate.catalog.search")
record SearchProperties(double matchThreshold, int goodMatches, Duration cacheTtl, int musicBrainzLimit) {
}
