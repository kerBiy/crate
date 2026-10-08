package io.github.kerbiy.crate.user.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.user.Accounts;
import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** GET /users/search?q=: finding people by username or display name. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class UserSearchIntegrationTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    // Other test classes share the database, so every name here carries a tag no one else uses.
    private final String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @Test
    void matchesUsernameAndDisplayNameIgnoringCase() {
        User byName = Accounts.register(client, users, "zed_" + tag);
        User byDisplay = Accounts.register(client, users, "other_" + tag.substring(0, 4));
        Accounts.setDisplayName(jdbc, byDisplay, "Zed " + tag.toUpperCase());

        List<String> found = usernames(search("ZED"));

        assertThat(found).contains(byName.getUsername(), byDisplay.getUsername());
    }

    @Test
    void exactUsernameFirstThenPrefixThenTheRest() {
        Accounts.register(client, users, "x" + tag + "_b");    // contains only
        Accounts.register(client, users, tag + "_a");          // prefix
        Accounts.register(client, users, tag);                 // exact

        assertThat(usernames(search(tag))).containsExactly(tag, tag + "_a", "x" + tag + "_b");
    }

    @Test
    void wildcardsAreLiteral() {
        Accounts.register(client, users, "a" + tag);

        // "_" would match any single character, and "%" anything, if they weren't escaped.
        assertThat(usernames(search("_" + tag))).isEmpty();
        assertThat(usernames(search("%" + tag))).isEmpty();
    }

    @Test
    void limitCapsTheResults() {
        for (int i = 0; i < 3; i++) {
            Accounts.register(client, users, tag + "_" + i);
        }

        assertThat(usernames(search(tag + "&limit=2"))).hasSize(2);
    }

    @Test
    void blankOrTooLongQueryIs400() {
        client.get().uri("/users/search?q={q}", "  ").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("q");
        client.get().uri("/users/search?q=" + "a".repeat(51)).exchange()
                .expectStatus().isBadRequest();
        client.get().uri("/users/search").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void neverShowsEmail() {
        Accounts.register(client, users, "mail_" + tag);

        client.get().uri("/users/search?q=mail_" + tag).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].username").isEqualTo("mail_" + tag)
                .jsonPath("$.items[0].email").doesNotExist();
    }

    private List<Map<String, Object>> search(String query) {
        Result result = client.get().uri("/users/search?q=" + query).exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Result>() {})
                .returnResult().getResponseBody();
        return result.items();
    }

    private static List<String> usernames(List<Map<String, Object>> items) {
        return items.stream().map(item -> (String) item.get("username")).toList();
    }

    record Result(List<Map<String, Object>> items) {
    }
}
