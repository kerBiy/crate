package io.github.kerbiy.crate.catalog.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.catalog.search}.
 *
 * @param matchThreshold   minimum pg_trgm word similarity (0–1) for an album to match locally; only
 *                         used when MusicBrainz can't be reached
 * @param cacheTtl         how long a query MusicBrainz already answered is served from our DB
 * @param cacheTtlWithoutListeners the same for a query ranked without ListenBrainz counts (it failed):
 *                         shorter, so an outage doesn't keep worse rankings for long (ADR 012)
 * @param musicBrainzLimit how many release groups to request per MusicBrainz search (max 100)
 * @param popularityWeight how much the number of releases counts next to MusicBrainz's score
 *                         (see {@link SearchRanking#relevance})
 * @param listenerWeight   how much the ListenBrainz listener count counts next to it (ADR 012)
 * @param coverCheckCount  how many of the top-ranked albums get a cover check on a cold search:
 *                         the largest page a client can ask for
 */
@ConfigurationProperties("crate.catalog.search")
record SearchProperties(double matchThreshold, Duration cacheTtl, Duration cacheTtlWithoutListeners,
        int musicBrainzLimit, double popularityWeight, double listenerWeight, int coverCheckCount) {
}
