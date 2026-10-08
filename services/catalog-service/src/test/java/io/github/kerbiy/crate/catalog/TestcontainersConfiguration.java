package io.github.kerbiy.crate.catalog;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared by every integration test: all of them @Import this, so Spring caches one context and
 * starts one Postgres container, one Kafka broker and one WireMock "MusicBrainz" for the whole test run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same image as infra/compose. @ServiceConnection points the datasource at this container,
    // so the tests need no URL/password properties and never read infra/compose/.env.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17.11-alpine");
    }

    // Same image as infra/compose, and like there, topics aren't created on first use.
    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    // Stands in for musicbrainz.org (under /ws/2) and coverartarchive.org (under /caa);
    // serves the recorded fixtures under src/test/resources/__files.
    @Bean(destroyMethod = "stop")
    WireMockServer musicBrainz() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar musicBrainzProperties(WireMockServer musicBrainz) {
        return registry -> {
            registry.add("crate.musicbrainz.base-url", () -> musicBrainz.baseUrl() + "/ws/2");
            registry.add("MUSICBRAINZ_CONTACT", () -> "test@example.com");
            // The real limiter and retry code, without the real waiting.
            registry.add("crate.musicbrainz.rate-limit.interval", () -> "1ms");
            registry.add("crate.musicbrainz.retry.initial-backoff", () -> "1ms");
            registry.add("crate.catalog.cover-art.base-url", () -> musicBrainz.baseUrl() + "/caa");
            registry.add("crate.catalog.cover-art.timeout", () -> "500ms");
        };
    }
}
