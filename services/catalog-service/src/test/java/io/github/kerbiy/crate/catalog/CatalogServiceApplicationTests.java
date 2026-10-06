package io.github.kerbiy.crate.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
// Real config otherwise, so this also proves the MusicBrainz beans wire up (no request is sent).
@TestPropertySource(properties = "MUSICBRAINZ_CONTACT=test@example.com")
class CatalogServiceApplicationTests {

    // Same image as infra/compose. @ServiceConnection points the datasource at this container,
    // so the test needs no URL/password properties and never reads infra/compose/.env.
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-alpine");

    @Autowired
    RestTestClient client;

    @Autowired
    Flyway flyway;

    @Test
    void healthEndpointReportsUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void flywayAppliedBaselineMigration() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }
}
