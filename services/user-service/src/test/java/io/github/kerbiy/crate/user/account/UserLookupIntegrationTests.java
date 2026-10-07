package io.github.kerbiy.crate.user.account;

import static io.github.kerbiy.crate.user.TestcontainersConfiguration.INVITE_CODE;

import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** GET /users?ids=: names for a list of reviews or feed items. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class UserLookupIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Test
    void returnsUsersInRequestOrderAndSkipsUnknownIds() {
        User ana = register();
        User bo = register();
        UUID unknown = UUID.randomUUID();

        client.get().uri("/users?ids={ids}", bo.getId() + "," + unknown + "," + ana.getId() + "," + bo.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].id").isEqualTo(bo.getId().toString())
                .jsonPath("$.items[0].username").isEqualTo(bo.getUsername())
                .jsonPath("$.items[1].id").isEqualTo(ana.getId().toString())
                // Public view: never the email or the password hash.
                .jsonPath("$.items[0].email").doesNotExist()
                .jsonPath("$.items[0].passwordHash").doesNotExist();
    }

    @Test
    void moreThan100IdsIsRejected() {
        String ids = Collections.nCopies(101, UUID.randomUUID().toString()).stream().collect(Collectors.joining(","));

        client.get().uri("/users?ids={ids}", ids)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                .jsonPath("$.errors[0].field").isEqualTo("ids");
    }

    @Test
    void idThatIsNotAUuidIsRejected() {
        client.get().uri("/users?ids=nope")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
    }

    @Test
    void missingIdsIsRejected() {
        client.get().uri("/users")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
    }

    private User register() {
        String username = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@example.com",
                        "password", "correct horse battery", "inviteCode", INVITE_CODE))
                .exchange()
                .expectStatus().isCreated();
        return users.findByUsername(username).orElseThrow();
    }
}
