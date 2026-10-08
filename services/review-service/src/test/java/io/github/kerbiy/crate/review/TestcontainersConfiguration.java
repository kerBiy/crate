package io.github.kerbiy.crate.review;

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
 * starts one Postgres container, one Kafka broker and one WireMock "catalog-service" for the whole
 * test run.
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

    // Same image as infra/compose, and like there, topics aren't created on first use: the
    // review.events topic must come from our own NewTopic bean.
    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    // Stands in for catalog-service's GET /albums/{id}.
    @Bean(destroyMethod = "stop")
    WireMockServer catalog() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar catalogProperties(WireMockServer catalog) {
        return registry -> {
            registry.add("crate.catalog.base-url", catalog::baseUrl);
            // The real timeout code, without waiting 2 s per test.
            registry.add("crate.catalog.timeout", () -> "500ms");
        };
    }
}
