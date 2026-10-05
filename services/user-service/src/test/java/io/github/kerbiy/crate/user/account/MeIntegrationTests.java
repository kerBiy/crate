package io.github.kerbiy.crate.user.account;

import static io.github.kerbiy.crate.user.TestcontainersConfiguration.INVITE_CODE;

import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Calls the service directly, playing the gateway by setting X-User-Id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class MeIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Test
    void returnsTheUserNamedByXUserId() {
        String username = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@example.com",
                        "password", "correct horse battery", "inviteCode", INVITE_CODE))
                .exchange()
                .expectStatus().isCreated();
        User user = users.findByUsername(username).orElseThrow();

        client.get().uri("/users/me")
                .header("X-User-Id", user.getId().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(user.getId().toString())
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.email").isEqualTo(username + "@example.com")
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.passwordHash").doesNotExist();
    }

    @Test
    void missingXUserIdIs401() {
        client.get().uri("/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:unauthenticated");
    }

    @Test
    void unknownUserIs404() {
        client.get().uri("/users/me")
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:user-not-found");
    }

    @Test
    void malformedXUserIdIs400() {
        client.get().uri("/users/me")
                .header("X-User-Id", "not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
