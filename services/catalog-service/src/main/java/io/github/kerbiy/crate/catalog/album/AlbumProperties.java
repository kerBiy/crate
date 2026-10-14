package io.github.kerbiy.crate.catalog.album;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code crate.catalog.album}.
 *
 * @param recheckEmptyTracksAfter an album MusicBrainz had no release for is asked again after this long:
 *                                people add releases later
 */
@ConfigurationProperties("crate.catalog.album")
record AlbumProperties(Duration recheckEmptyTracksAfter) {
}
