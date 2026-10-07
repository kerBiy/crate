package io.github.kerbiy.crate.review.review;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.kerbiy.crate.review.ReviewIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** PUT, DELETE and GET of my own review. */
class ReviewWriteIntegrationTests extends ReviewIntegrationTest {

    private final UUID me = UUID.randomUUID();
    private final UUID album = UUID.randomUUID();

    // --- PUT /reviews/albums/{albumId} ---

    @Test
    void firstRatingCreatesReview() {
        put(me, album, 9, "The best one, no debate.")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.userId").isEqualTo(me.toString())
                .jsonPath("$.albumId").isEqualTo(album.toString())
                .jsonPath("$.rating").isEqualTo(9)
                .jsonPath("$.body").isEqualTo("The best one, no debate.")
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.updatedAt").exists();

        assertThat(countReviews()).isEqualTo(1);
        catalog.verify(1, getRequestedFor(urlPathEqualTo("/albums/" + album)));
    }

    @Test
    void secondPutReplacesTheReview() {
        put(me, album, 6, "Fine.").expectStatus().isCreated();

        put(me, album, 8, null)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rating").isEqualTo(8)
                .jsonPath("$.body").doesNotExist();

        assertThat(countReviews()).isEqualTo(1);
        int version = jdbc.sql("select version from reviews").query(Integer.class).single();
        assertThat(version).isEqualTo(1);
    }

    @Test
    void repeatingTheSamePutChangesNothing() {
        put(me, album, 7, "Same").expectStatus().isCreated();

        put(me, album, 7, "Same").expectStatus().isOk();

        // No real change, so no update ran: the version wasn't bumped.
        int version = jdbc.sql("select version from reviews").query(Integer.class).single();
        assertThat(version).isZero();
    }

    @Test
    void blankBodyIsStoredAsNoText() {
        put(me, album, 7, "   ").expectStatus().isCreated()
                .expectBody().jsonPath("$.body").doesNotExist();
    }

    @Test
    void ratingOutOfRangeIsRejected() {
        for (int rating : new int[] {0, 11}) {
            put(me, album, rating, null)
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                    .jsonPath("$.errors[0].field").isEqualTo("rating");
        }
        assertThat(countReviews()).isZero();
    }

    @Test
    void missingRatingIsRejected() {
        put(me, album, null, "Text alone isn't a review")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("rating");
    }

    @Test
    void bodyLongerThan5000CharactersIsRejected() {
        put(me, album, 8, "a".repeat(5001))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("body");

        put(me, album, 8, "a".repeat(5000)).expectStatus().isCreated();
    }

    @Test
    void putWithoutUserIsUnauthorized() {
        client.put().uri("/reviews/albums/{albumId}", album)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"rating\": 8}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:unauthenticated");
    }

    // --- catalog-service check ---

    @Test
    void albumCatalogDoesNotKnowIs404() {
        stubCatalog(aResponse().withStatus(404));

        put(me, album, 8, null)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:album-not-found");

        assertThat(countReviews()).isZero();
    }

    @Test
    void catalogUnavailableIs503NotNotFound() {
        stubCatalog(aResponse().withStatus(503));

        assertCatalogUnavailable();
    }

    @Test
    void catalogServerErrorIs503() {
        stubCatalog(aResponse().withStatus(500));

        assertCatalogUnavailable();
    }

    @Test
    void catalogSlowerThanTheTimeoutIs503() {
        // The tests' timeout is 500 ms.
        stubCatalog(aResponse().withStatus(200).withFixedDelay(1_500));

        assertCatalogUnavailable();
    }

    @Test
    void catalogDroppingTheConnectionIs503() {
        stubCatalog(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        assertCatalogUnavailable();
    }

    private void stubCatalog(ResponseDefinitionBuilder response) {
        catalog.stubFor(get(urlPathEqualTo("/albums/" + album)).atPriority(1).willReturn(response));
    }

    private void assertCatalogUnavailable() {
        put(me, album, 8, null)
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:catalog-unavailable");
        assertThat(countReviews()).isZero();
    }

    // --- concurrency ---

    @Test
    void doubleClickNeverFails() throws Exception {
        int clicks = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < clicks; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return put(me, album, 8, "Twice").returnResult(String.class).getStatus().value();
                }));
            }
            // Release every request at once, so their transactions really overlap.
            start.countDown();
        }

        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            codes.add(status.get());
        }
        // Exactly one request created the review; every other one became an update of it.
        assertThat(codes).containsOnly(200, 201).filteredOn(code -> code == 201).hasSize(1);
        assertThat(countReviews()).isEqualTo(1);
    }

    @Test
    void concurrentChangesAllSucceedAndOneWins() throws Exception {
        put(me, album, 2, null).expectStatus().isCreated();
        List<Integer> ratings = List.of(4, 6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int rating : ratings) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return put(me, album, rating, null).returnResult(String.class).getStatus().value();
                }));
            }
            start.countDown();
        }

        for (Future<Integer> status : statuses) {
            assertThat(status.get()).isEqualTo(200);
        }
        int stored = jdbc.sql("select rating from reviews").query(Integer.class).single();
        assertThat(stored).isIn(4, 6);
    }

    // --- DELETE and GET me ---

    @Test
    void myReviewCanBeReadThenDeleted() {
        put(me, album, 7, "Grower").expectStatus().isCreated();

        client.get().uri("/reviews/albums/{albumId}/me", album).header("X-User-Id", me.toString()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.rating").isEqualTo(7).jsonPath("$.body").isEqualTo("Grower");

        delete(me).expectStatus().isNoContent();

        client.get().uri("/reviews/albums/{albumId}/me", album).header("X-User-Id", me.toString()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:review-not-found");
        assertThat(countReviews()).isZero();
    }

    @Test
    void deleteIsIdempotentAndOnlyTouchesMyReview() {
        UUID friend = UUID.randomUUID();
        put(friend, album, 9, null).expectStatus().isCreated();

        delete(me).expectStatus().isNoContent();
        delete(me).expectStatus().isNoContent();

        assertThat(countReviews()).isEqualTo(1);
    }

    @Test
    void myReviewNeedsASignedInUser() {
        client.get().uri("/reviews/albums/{albumId}/me", album).exchange().expectStatus().isUnauthorized();
        client.delete().uri("/reviews/albums/{albumId}", album).exchange().expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec delete(UUID userId) {
        return client.delete().uri("/reviews/albums/{albumId}", album).header("X-User-Id", userId.toString())
                .exchange();
    }
}
