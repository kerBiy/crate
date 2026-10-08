package io.github.kerbiy.crate.user.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kerbiy.crate.user.Accounts;
import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** PUT and DELETE /users/{id}/follow. Calls the service directly, playing the gateway with X-User-Id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class FollowIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    User ana;
    User bo;

    @BeforeEach
    void twoUsers() {
        ana = Accounts.register(client, users);
        bo = Accounts.register(client, users);
    }

    @Test
    void followCreatesTheFollow() {
        follow(ana, bo.getId()).expectStatus().isNoContent();

        assertThat(rows(ana, bo)).isEqualTo(1);
        assertThat(rows(bo, ana)).isZero(); // asymmetric
    }

    @Test
    void followingTwiceIsHarmless() {
        follow(ana, bo.getId()).expectStatus().isNoContent();
        follow(ana, bo.getId()).expectStatus().isNoContent();

        assertThat(rows(ana, bo)).isEqualTo(1);
    }

    @Test
    void unfollowRemovesTheFollowAndRepeatingItIsHarmless() {
        follow(ana, bo.getId()).expectStatus().isNoContent();

        unfollow(ana, bo.getId()).expectStatus().isNoContent();
        unfollow(ana, bo.getId()).expectStatus().isNoContent();

        assertThat(rows(ana, bo)).isZero();
    }

    @Test
    void unfollowingSomeoneUnknownIsStill204() {
        unfollow(ana, UUID.randomUUID()).expectStatus().isNoContent();
    }

    @Test
    void followingYourselfIsRejected() {
        follow(ana, ana.getId())
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:cannot-follow-self");

        assertThat(rows(ana, ana)).isZero();
    }

    @Test
    void theTableRejectsASelfFollowEvenWithoutTheApi() {
        assertThatThrownBy(() -> jdbc.sql("insert into follows (follower_id, followee_id) values (:id, :id)")
                .param("id", ana.getId()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void followingAnUnknownUserIs404() {
        follow(ana, UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:user-not-found");
    }

    @Test
    void followAsAUserThatNoLongerExistsIs404() {
        client.put().uri("/users/{id}/follow", bo.getId())
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:user-not-found");
    }

    @Test
    void followAndUnfollowNeedASignedInUser() {
        client.put().uri("/users/{id}/follow", bo.getId()).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:unauthenticated");
        client.delete().uri("/users/{id}/follow", bo.getId()).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void idThatIsNotAUuidIs400() {
        client.put().uri("/users/nope/follow").header("X-User-Id", ana.getId().toString()).exchange()
                .expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec follow(User me, UUID target) {
        return client.put().uri("/users/{id}/follow", target).header("X-User-Id", me.getId().toString()).exchange();
    }

    private RestTestClient.ResponseSpec unfollow(User me, UUID target) {
        return client.delete().uri("/users/{id}/follow", target).header("X-User-Id", me.getId().toString()).exchange();
    }

    private long rows(User follower, User followee) {
        return jdbc.sql("select count(*) from follows where follower_id = :a and followee_id = :b")
                .param("a", follower.getId()).param("b", followee.getId())
                .query(Long.class).single();
    }
}
