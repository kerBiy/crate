package io.github.kerbiy.crate.user.follow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.user.Accounts;
import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;

/** GET /users/{id}/followers, /following (keyset pagination) and /following/ids. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class FollowListIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Test
    void followersArePagedNewestFirstWithoutGapsOrDuplicates() {
        User star = Accounts.register(client, users);
        List<User> fans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User fan = Accounts.register(client, users);
            follow(fan, star);
            fans.add(fan);
        }

        Page first = page("/users/{id}/followers?limit=2", star.getId(), null);
        Page second = page("/users/{id}/followers?limit=2", star.getId(), first.nextCursor());
        Page third = page("/users/{id}/followers?limit=2", star.getId(), second.nextCursor());

        assertThat(third.nextCursor()).isNull();
        List<String> seen = new ArrayList<>();
        for (Page page : List.of(first, second, third)) {
            page.items().forEach(item -> seen.add((String) item.get("id")));
        }
        assertThat(seen).containsExactlyElementsOf(fans.reversed().stream().map(u -> u.getId().toString()).toList());
        // Public view only.
        assertThat(first.items().getFirst()).containsOnlyKeys("id", "username", "displayName");
    }

    @Test
    void followingListsWhoTheUserFollows() {
        User ana = Accounts.register(client, users);
        User bo = Accounts.register(client, users);
        User cy = Accounts.register(client, users);
        follow(ana, bo);
        follow(ana, cy);
        follow(bo, ana); // not in ana's following

        Page page = page("/users/{id}/following", ana.getId(), null);

        assertThat(page.items()).extracting(item -> item.get("username"))
                .containsExactly(cy.getUsername(), bo.getUsername());
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void followingIdsListsEveryoneFollowed() {
        User ana = Accounts.register(client, users);
        User bo = Accounts.register(client, users);
        User cy = Accounts.register(client, users);
        follow(ana, bo);
        follow(ana, cy);

        client.get().uri("/users/{id}/following/ids", ana.getId()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ids.length()").isEqualTo(2)
                .jsonPath("$.ids[0]").isEqualTo(cy.getId().toString())
                .jsonPath("$.ids[1]").isEqualTo(bo.getId().toString());
    }

    @Test
    void followingIdsOfSomeoneWhoFollowsNobodyIsEmpty() {
        User loner = Accounts.register(client, users);

        client.get().uri("/users/{id}/following/ids", loner.getId()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ids.length()").isEqualTo(0);
    }

    @Test
    void listOfAnUnknownUserIs404() {
        client.get().uri("/users/{id}/followers", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:user-not-found");
    }

    @Test
    void badCursorIs400() {
        User ana = Accounts.register(client, users);

        client.get().uri("/users/{id}/following?cursor=nope", ana.getId()).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("cursor");
    }

    @Test
    void limitAbove50Is400() {
        User ana = Accounts.register(client, users);

        client.get().uri("/users/{id}/followers?limit=51", ana.getId()).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("limit");
    }

    private void follow(User me, User target) {
        client.put().uri("/users/{id}/follow", target.getId()).header("X-User-Id", me.getId().toString())
                .exchange().expectStatus().isNoContent();
    }

    private Page page(String uri, UUID id, String cursor) {
        String full = cursor == null ? uri : uri + (uri.contains("?") ? "&" : "?") + "cursor=" + cursor;
        return client.get().uri(full, id).exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Page>() {})
                .returnResult().getResponseBody();
    }

    record Page(List<Map<String, Object>> items, String nextCursor) {
    }
}
