package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MusicBrainzProperties.class)
class MusicBrainzConfig {

    // One bucket for the whole process: every MusicBrainz request shares it.
    @Bean
    TokenBucketRateLimiter musicBrainzRateLimiter(MusicBrainzProperties properties) {
        return TokenBucketRateLimiter.realTime(properties.rateLimit().interval(), properties.rateLimit().maxWait());
    }

    @Bean
    MusicBrainzClient musicBrainzClient(MusicBrainzProperties properties, TokenBucketRateLimiter rateLimiter) {
        return MusicBrainzClient.create(properties, rateLimiter, Sleeper.REAL,
                () -> ThreadLocalRandom.current().nextDouble());
    }
}
