package io.github.kerbiy.crate.review.review;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.kerbiy.crate.review.ReviewIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

/** GET /reviews/feed: feed v1, fan-out on read. WireMock plays user-service. */
class FeedIntegrationTests extends ReviewIntegrationTest {

    private final UUID me = UUID.randomUUID();

    @Test
    void showsOnlyReviewsOfPeopleIFollowNewestFirst() {
        UUID ana = UUID.randomUUID();
        UUID bo = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        put(ana, UUID.randomUUID(), 8, "First").expectStatus().isCreated();
        put(stranger, UUID.randomUUID(), 2, "Not a friend").expectStatus().isCreated();
        put(bo, UUID.randomUUID(), 10, "Second").expectStatus().isCreated();
        put(me, UUID.randomUUID(), 6, "Mine").expectStatus().isCreated();
        put(ana, UUID.randomUUID(), 4, "Third").expectStatus().isCreated();
        stubFollowing(me, ana, bo);

        Page page = feed(null, 20);

        assertThat(page.items()).extracting(item -> item.get("body")).containsExactly("Third", "Second", "First");
        assertThat(page.items().getFirst()).containsKeys("id", "userId", "albumId", "rating", "createdAt");
        assertThat(page.nextCursor()).isNull();
        users.verify(getRequestedFor(urlPathEqualTo("/users/" + me + "/following/ids")));
    }

    @Test
    void pagesWithoutGapsOrDuplicates() {
        UUID ana = UUID.randomUUID();
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add("Review " + i);
            put(ana, UUID.randomUUID(), 7, "Review " + i).expectStatus().isCreated();
        }
        stubFollowing(me, ana);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Page page = feed(cursor, 2);
            page.items().forEach(item -> seen.add((String) item.get("body")));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(created.reversed());
    }

    @Test
    void followingNobodyIsAnEmptyFeed() {
        put(UUID.randomUUID(), UUID.randomUUID(), 7, null).expectStatus().isCreated();
        stubFollowing(me);

        Page page = feed(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void userServiceErrorIs503() {
        users.stubFor(get(urlPathEqualTo("/users/" + me + "/following/ids")).willReturn(aResponse().withStatus(500)));

        expectUnavailable();
    }

    @Test
    void userServiceTooSlowIs503WithinTheTimeout() {
        // Test timeout is 500 ms (TestcontainersConfiguration); user-service answers after 3 s.
        users.stubFor(get(urlPathEqualTo("/users/" + me + "/following/ids"))
                .willReturn(okJson("{\"ids\": []}").withFixedDelay(3000)));

        long start = System.nanoTime();
        expectUnavailable();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void userServiceDroppingTheConnectionIs503() {
        users.stubFor(get(urlPathEqualTo("/users/" + me + "/following/ids"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        expectUnavailable();
    }

    @Test
    void userServiceSendingGarbageIs503() {
        users.stubFor(get(urlPathEqualTo("/users/" + me + "/following/ids")).willReturn(okJson("not json")));

        expectUnavailable();
    }

    @Test
    void feedNeedsASignedInUser() {
        client.get().uri("/reviews/feed").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:unauthenticated");
    }

    @Test
    void invalidCursorIsRejected() {
        stubFollowing(me, UUID.randomUUID());

        client.get().uri("/reviews/feed?cursor=nope").header("X-User-Id", me.toString()).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("cursor");
    }

    private void expectUnavailable() {
        client.get().uri("/reviews/feed").header("X-User-Id", me.toString()).exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:user-service-unavailable");
    }

    private void stubFollowing(UUID user, UUID... following) {
        String ids = List.of(following).stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        users.stubFor(get(urlPathEqualTo("/users/" + user + "/following/ids")).willReturn(okJson("{\"ids\": [" + ids + "]}")));
    }

    private static ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private Page feed(String cursor, int limit) {
        return client.get()
                .uri(builder -> {
                    builder.path("/reviews/feed").queryParam("limit", limit);
                    if (cursor != null) {
                        builder.queryParam("cursor", cursor);
                    }
                    return builder.build();
                })
                .header("X-User-Id", me.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Page>() {})
                .returnResult().getResponseBody();
    }

    record Page(List<Map<String, Object>> items, String nextCursor) {
    }
}
