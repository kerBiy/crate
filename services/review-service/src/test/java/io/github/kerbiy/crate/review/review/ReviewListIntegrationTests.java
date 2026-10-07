package io.github.kerbiy.crate.review.review;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.review.ReviewIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** GET /reviews/albums/{albumId} and GET /reviews/users/{userId}: keyset pagination. */
class ReviewListIntegrationTests extends ReviewIntegrationTest {

    private final UUID album = UUID.randomUUID();

    @Test
    void albumReviewsArePagedNewestFirstWithoutGapsOrDuplicates() {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID user = UUID.randomUUID();
            users.add(user);
            put(user, album, i + 1, null).expectStatus().isCreated();
        }
        put(UUID.randomUUID(), UUID.randomUUID(), 5, null).expectStatus().isCreated(); // another album

        Page first = albumPage(null, 2);
        Page second = albumPage(first.nextCursor(), 2);
        Page third = albumPage(second.nextCursor(), 2);

        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).isNull();
        List<String> seen = new ArrayList<>();
        for (Page page : List.of(first, second, third)) {
            page.items().forEach(item -> seen.add((String) item.get("userId")));
        }
        // Newest first: the reverse of the order they were created in.
        assertThat(seen).containsExactlyElementsOf(users.reversed().stream().map(UUID::toString).toList());
    }

    @Test
    void reviewAddedWhilePagingDoesNotShiftLaterPages() {
        for (int i = 0; i < 4; i++) {
            put(UUID.randomUUID(), album, 8, null).expectStatus().isCreated();
        }
        Page first = albumPage(null, 2);

        // OFFSET would now return the last item of page 1 again; a cursor doesn't.
        put(UUID.randomUUID(), album, 3, null).expectStatus().isCreated();
        Page second = albumPage(first.nextCursor(), 2);

        assertThat(second.items()).hasSize(2);
        assertThat(ids(second)).doesNotContainAnyElementsOf(ids(first));
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void reviewsCreatedInTheSameMicrosecondAreNeitherSkippedNorRepeated() {
        // Same created_at for all three: only the id tie-breaker keeps the pages apart.
        Instant same = Instant.parse("2026-10-07T12:00:00.123456Z");
        for (int i = 0; i < 3; i++) {
            jdbc.sql("""
                    insert into reviews (id, user_id, album_id, rating, created_at, updated_at)
                    values (:id, :user, :album, 8, :at, :at)""")
                    .param("id", UUID.randomUUID()).param("user", UUID.randomUUID()).param("album", album)
                    .param("at", java.sql.Timestamp.from(same)).update();
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            Page page = albumPage(cursor, 1);
            seen.addAll(ids(page));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(seen).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void albumWithoutReviewsIsAnEmptyPage() {
        Page page = albumPage(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void userReviewsListOnlyThatUser() {
        UUID user = UUID.randomUUID();
        put(user, UUID.randomUUID(), 6, "One").expectStatus().isCreated();
        put(user, UUID.randomUUID(), 7, "Two").expectStatus().isCreated();
        put(UUID.randomUUID(), album, 9, null).expectStatus().isCreated();

        Page page = client.get().uri("/reviews/users/{userId}", user).exchange()
                .expectStatus().isOk()
                .expectBody(Page.class).returnResult().getResponseBody();

        assertThat(page.items()).extracting(item -> item.get("body")).containsExactly("Two", "One");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void invalidCursorIsRejected() {
        client.get().uri("/reviews/albums/{albumId}?cursor=not-a-cursor", album).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                .jsonPath("$.errors[0].field").isEqualTo("cursor");
    }

    @Test
    void limitIsBetween1And50() {
        for (String limit : List.of("0", "51")) {
            client.get().uri("/reviews/albums/{albumId}?limit={limit}", album, limit).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errors[0].field").isEqualTo("limit");
        }
        client.get().uri("/reviews/users/{userId}?limit=51", UUID.randomUUID()).exchange()
                .expectStatus().isBadRequest();
    }

    record Page(List<Map<String, Object>> items, String nextCursor) {
    }

    private Page albumPage(String cursor, int limit) {
        return client.get()
                .uri(builder -> {
                    builder.path("/reviews/albums/{albumId}").queryParam("limit", limit);
                    if (cursor != null) {
                        builder.queryParam("cursor", cursor);
                    }
                    return builder.build(album);
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Page>() {})
                .returnResult().getResponseBody();
    }

    private static List<String> ids(Page page) {
        return page.items().stream().map(item -> (String) item.get("id")).toList();
    }
}
