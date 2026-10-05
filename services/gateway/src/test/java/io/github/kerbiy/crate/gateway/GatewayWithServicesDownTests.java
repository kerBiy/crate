package io.github.kerbiy.crate.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The gateway while user-service (and the rest) can't be reached. Nothing listens on port 1. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "crate.services.user-service=http://localhost:1",
        "crate.services.catalog-service=http://localhost:1",
        "crate.services.review-service=http://localhost:1"
})
class GatewayWithServicesDownTests {

    @Autowired
    WebTestClient client;

    // The JWK Set is fetched lazily, on the first token, so startup doesn't need user-service.
    @Test
    void startsAndReportsUpWhileServicesAreDown() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    // The token may well be valid; we just can't check it. 401 would wrongly tell the client to log out.
    @Test
    void tokenIs503ProblemWhenSigningKeysCantBeFetched() {
        String token = TestTokens.mint(TestTokens.rsaKey("any-key"), UUID.randomUUID(), "alice",
                Instant.now(), Duration.ofHours(12));

        byte[] content = client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:authentication-unavailable")
                .jsonPath("$.title").isEqualTo("Authentication unavailable")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.instance").isEqualTo("/api/users/me")
                .returnResult().getResponseBodyContent();
        String body = new String(content, StandardCharsets.UTF_8);

        // No internals: no hosts, ports, exception names or library messages.
        assertThat(body).doesNotContainIgnoringCase("localhost")
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("connect")
                .doesNotContainIgnoringCase("jwks")
                .doesNotContain("Could not obtain");
    }

    // A malformed token fails before any key is needed: still the client's fault, still 401.
    @Test
    void malformedTokenIsStill401WhileServicesAreDown() {
        client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth("not-a-jwt"))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
