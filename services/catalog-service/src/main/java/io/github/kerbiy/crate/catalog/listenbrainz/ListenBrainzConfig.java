package io.github.kerbiy.crate.catalog.listenbrainz;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzProperties;
import io.github.kerbiy.crate.catalog.musicbrainz.TokenBucketRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ListenBrainzProperties.class)
class ListenBrainzConfig {

    // ListenBrainz is a MetaBrainz project: same User-Agent, same contact. Its rate limiter is its own
    // (not a bean, so the MusicBrainz one stays the only TokenBucketRateLimiter in the context).
    @Bean
    ListenBrainzClient listenBrainzClient(ListenBrainzProperties properties, MusicBrainzProperties musicBrainz) {
        TokenBucketRateLimiter rateLimiter = TokenBucketRateLimiter.realTime(
                properties.rateLimit().interval(), properties.rateLimit().maxWait());
        return ListenBrainzClient.create(properties, musicBrainz.userAgent(), rateLimiter);
    }
}
