package io.github.kerbiy.crate.user;

import static io.github.kerbiy.crate.user.TestcontainersConfiguration.INVITE_CODE;

import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Test helper: registers accounts through the real endpoint, with unique names so tests don't collide. */
public final class Accounts {

    private Accounts() {
    }

    public static User register(RestTestClient client, UserRepository users) {
        return register(client, users, "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    public static User register(RestTestClient client, UserRepository users, String username) {
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@example.com",
                        "password", "correct horse battery", "inviteCode", INVITE_CODE))
                .exchange()
                .expectStatus().isCreated();
        return users.findByUsername(username.toLowerCase()).orElseThrow();
    }

    /** There's no endpoint for it yet (PATCH /users/me comes later), so set it in the table. */
    public static void setDisplayName(JdbcClient jdbc, User user, String displayName) {
        jdbc.sql("update users set display_name = :name where id = :id")
                .param("name", displayName).param("id", user.getId()).update();
    }
}
