package io.github.kerbiy.crate.user.profile;

import io.github.kerbiy.crate.user.Accounts;
import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** GET /users/{username}: public profile with follow counts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProfileIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    @Test
    void showsCountsAndWhetherTheViewerFollows() {
        User ana = Accounts.register(client, users);
        User bo = Accounts.register(client, users);
        User cy = Accounts.register(client, users);
        Accounts.setDisplayName(jdbc, ana, "Ana");
        follow(bo, ana);
        follow(cy, ana);
        follow(ana, bo);

        client.get().uri("/users/{username}", ana.getUsername())
                .header("X-User-Id", bo.getId().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ana.getId().toString())
                .jsonPath("$.username").isEqualTo(ana.getUsername())
                .jsonPath("$.displayName").isEqualTo("Ana")
                .jsonPath("$.followerCount").isEqualTo(2)
                .jsonPath("$.followingCount").isEqualTo(1)
                .jsonPath("$.followedByMe").isEqualTo(true)
                .jsonPath("$.email").doesNotExist();
    }

    @Test
    void followedByMeIsFalseForSomeoneINeverFollowedAndForMyself() {
        User ana = Accounts.register(client, users);
        User bo = Accounts.register(client, users);
        follow(ana, bo);

        profile(ana.getUsername(), bo).expectBody().jsonPath("$.followedByMe").isEqualTo(false);
        profile(ana.getUsername(), ana).expectBody().jsonPath("$.followedByMe").isEqualTo(false);
    }

    @Test
    void usernameIsCaseInsensitive() {
        User ana = Accounts.register(client, users);

        profile(ana.getUsername().toUpperCase(), ana).expectStatus().isOk()
                .expectBody().jsonPath("$.username").isEqualTo(ana.getUsername());
    }

    @Test
    void unknownUsernameIs404() {
        User ana = Accounts.register(client, users);

        profile("nobody_here_123", ana)
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:user-not-found");
    }

    @Test
    void literalPathsStillWin() {
        User ana = Accounts.register(client, users);

        // /users/me is the signed-in account (with email), not a profile called "me".
        profile("me", ana).expectStatus().isOk().expectBody().jsonPath("$.email").exists();
    }

    private RestTestClient.ResponseSpec profile(String username, User viewer) {
        return client.get().uri("/users/{username}", username).header("X-User-Id", viewer.getId().toString())
                .exchange();
    }

    private void follow(User me, User target) {
        client.put().uri("/users/{id}/follow", target.getId()).header("X-User-Id", me.getId().toString())
                .exchange().expectStatus().isNoContent();
    }
}
