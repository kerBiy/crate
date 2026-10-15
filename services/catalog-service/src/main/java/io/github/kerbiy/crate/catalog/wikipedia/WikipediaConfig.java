package io.github.kerbiy.crate.catalog.wikipedia;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzProperties;
import io.github.kerbiy.crate.catalog.musicbrainz.TokenBucketRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WikipediaProperties.class)
class WikipediaConfig {

    // Wikimedia's User-Agent policy: "<client>/<version> (<contact>) <library>/<version>". Same client and
    // contact as for MusicBrainz, plus the HTTP library. Its rate limiter is its own (not a bean, so the
    // MusicBrainz one stays the only TokenBucketRateLimiter in the context).
    @Bean
    WikipediaClient wikipediaClient(WikipediaProperties properties, MusicBrainzProperties musicBrainz) {
        String userAgent = musicBrainz.userAgent() + " Java-http-client/" + Runtime.version().feature();
        TokenBucketRateLimiter rateLimiter = TokenBucketRateLimiter.realTime(
                properties.rateLimit().interval(), properties.rateLimit().maxWait());
        return WikipediaClient.create(properties, userAgent, rateLimiter);
    }
}
