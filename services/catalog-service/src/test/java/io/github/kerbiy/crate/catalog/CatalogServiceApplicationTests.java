package io.github.kerbiy.crate.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Real config otherwise, so this also proves the MusicBrainz beans wire up (no request is sent).
class CatalogServiceApplicationTests extends CatalogIntegrationTest {

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
    void flywayAppliedAllMigrations() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
    }
}
