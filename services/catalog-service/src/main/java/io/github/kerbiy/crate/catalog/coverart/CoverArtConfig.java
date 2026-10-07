package io.github.kerbiy.crate.catalog.coverart;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CoverArtProperties.class)
class CoverArtConfig {

    // The Cover Art Archive is a MusicBrainz project: same User-Agent, same contact.
    @Bean
    CoverArtClient coverArtClient(CoverArtProperties properties, MusicBrainzProperties musicBrainz) {
        return CoverArtClient.create(properties, musicBrainz.userAgent());
    }

    @Bean
    CoverChecker coverChecker(CoverArtClient client, CoverArtProperties properties) {
        return new CoverChecker(client, properties.concurrency(), properties.deadline());
    }
}
