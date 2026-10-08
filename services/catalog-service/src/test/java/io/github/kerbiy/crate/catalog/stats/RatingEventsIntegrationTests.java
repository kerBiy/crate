package io.github.kerbiy.crate.catalog.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The review.events consumer, against a real Kafka broker. Events are hand-written JSON in the shape
 * of SPEC 6.1, so these tests check the wire contract, not just our own Java classes.
 */
// OutputCaptureExtension: the clamp tests check the warning that is logged.
@ExtendWith(OutputCaptureExtension.class)
class RatingEventsIntegrationTests extends CatalogIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private final UUID album = UUID.randomUUID();
    private final UUID review = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Test
    void reviewCreatedAddsToTheStatsShownOnTheAlbumPage() {
        ReleaseGroup stored = seed("OK Computer", "Radiohead");

        send(stored.id(), created(UUID.randomUUID(), stored.id(), 9));
        send(stored.id(), created(UUID.randomUUID(), stored.id(), 6));

        await().atMost(WAIT).untilAsserted(() -> assertThat(stats(stored.id())).isEqualTo(new Stats(2, 15,
                List.of(0, 0, 0, 0, 0, 1, 0, 0, 1, 0))));
        client.get().uri("/albums/{id}", stored.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ratingCount").isEqualTo(2)
                .jsonPath("$.avgRating").isEqualTo(3.8)
                .jsonPath("$.ratingDistribution").isEqualTo(List.of(0, 0, 0, 0, 0, 1, 0, 0, 1, 0));
    }

    @Test
    void albumWithoutRatingsHasAnEmptyDistribution() {
        ReleaseGroup stored = seed("Kid A", "Radiohead");

        client.get().uri("/albums/{id}", stored.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ratingCount").isEqualTo(0)
                .jsonPath("$.avgRating").doesNotExist()
                .jsonPath("$.ratingDistribution").isEqualTo(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void eventForAnAlbumCatalogHasNotStoredStillCounts() {
        // No FK from album_stats to albums (SPEC 5.3): the event may come before the album row.
        send(album, created(UUID.randomUUID(), album, 4));

        await().atMost(WAIT).untilAsserted(() -> assertThat(stats(album).count()).isEqualTo(1));
        assertThat(count("albums")).isZero();
    }

    @Test
    void sameEventTwiceIsCountedOnce() {
        UUID eventId = UUID.randomUUID();
        String event = created(eventId, album, 8);

        send(album, event);
        send(album, event);
        // A later event on the same key: same partition, so once it's processed, both copies were too.
        UUID sentinel = UUID.randomUUID();
        send(album, created(sentinel, album, 2));

        awaitProcessed(sentinel);
        assertThat(stats(album)).isEqualTo(new Stats(2, 10, List.of(0, 1, 0, 0, 0, 0, 0, 1, 0, 0)));
        assertThat(jdbc.sql("select count(*) from processed_events where event_id = :id").param("id", eventId)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void createdThenUpdatedThenDeletedLeavesNothing() {
        UUID createdId = UUID.randomUUID();
        UUID updatedId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();

        send(album, created(createdId, album, 3));
        send(album, updated(updatedId, album, 10, 3));
        awaitProcessed(updatedId);
        assertThat(stats(album)).isEqualTo(new Stats(1, 10, List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 1)));

        send(album, deleted(deletedId, album, 10));
        awaitProcessed(deletedId);
        assertThat(stats(album)).isEqualTo(new Stats(0, 0, List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void textOnlyUpdateChangesNoNumbers() {
        send(album, created(UUID.randomUUID(), album, 7));
        UUID updatedId = UUID.randomUUID();
        send(album, updated(updatedId, album, 7, 7));

        awaitProcessed(updatedId);
        assertThat(stats(album)).isEqualTo(new Stats(1, 7, List.of(0, 0, 0, 0, 0, 0, 1, 0, 0, 0)));
    }

    @Test
    void removingARatingThatWasNeverCountedClampsAtZero(CapturedOutput output) {
        // A ReviewDeleted with no ReviewCreated before it: that event was lost (ADR-006).
        UUID deletedId = UUID.randomUUID();
        send(album, deleted(deletedId, album, 9));
        awaitProcessed(deletedId);

        assertThat(stats(album)).isEqualTo(new Stats(0, 0, List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertThat(output).contains("Event " + deletedId + " would make album " + album);

        // No hidden negative left behind: the next rating counts as exactly one.
        UUID createdId = UUID.randomUUID();
        send(album, created(createdId, album, 8));
        awaitProcessed(createdId);
        assertThat(stats(album)).isEqualTo(new Stats(1, 8, List.of(0, 0, 0, 0, 0, 0, 0, 1, 0, 0)));
    }

    @Test
    void driftedStatsStayConsistent(CapturedOutput output) {
        send(album, created(UUID.randomUUID(), album, 7));
        // Neither the 9 nor the 5 was ever counted.
        UUID deletedId = UUID.randomUUID();
        send(album, deleted(deletedId, album, 9));
        UUID updatedId = UUID.randomUUID();
        send(album, updated(updatedId, album, 6, 5));
        awaitProcessed(updatedId);

        // Count and sum follow the buckets: the counted 7 and the new 6, nothing else.
        assertThat(stats(album)).isEqualTo(new Stats(2, 13, List.of(0, 0, 0, 0, 0, 1, 1, 0, 0, 0)));
        assertThat(output).contains("Event " + deletedId).contains("Event " + updatedId);
    }

    @Test
    void unknownEventTypeIsSkippedWithoutBlockingThePartition() {
        send(album, """
                {"eventId": "%s", "eventType": "ReviewLiked", "eventVersion": 1,
                 "occurredAt": "2026-10-03T18:21:07Z", "producer": "review-service", "payload": {}}
                """.formatted(UUID.randomUUID()));
        UUID next = UUID.randomUUID();
        send(album, created(next, album, 6));

        awaitProcessed(next);
        assertThat(stats(album).count()).isEqualTo(1);
    }

    // --- helpers ---

    private record Stats(int count, int sum, List<Integer> distribution) {
    }

    private Stats stats(UUID albumId) {
        return jdbc.sql("select rating_count, rating_sum, rating_distribution from album_stats where album_id = :id")
                .param("id", albumId)
                .query((rs, row) -> new Stats(rs.getInt(1), rs.getInt(2),
                        List.of((Integer[]) rs.getArray(3).getArray())))
                .optional()
                .orElse(new Stats(0, 0, List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(WAIT).until(() -> jdbc.sql("select count(*) from processed_events where event_id = :id")
                .param("id", eventId).query(Integer.class).single() == 1);
    }

    private void send(UUID key, String json) {
        // join(): the broker has stored it before the test goes on.
        kafka.send(ReviewEvents.TOPIC, key.toString(), json).join();
    }

    private String created(UUID eventId, UUID albumId, int rating) {
        return envelope(eventId, "ReviewCreated", """
                {"reviewId": "%s", "userId": "%s", "albumId": "%s", "rating": %d, "hasBody": true}
                """.formatted(review, user, albumId, rating));
    }

    private String updated(UUID eventId, UUID albumId, int rating, int oldRating) {
        return envelope(eventId, "ReviewUpdated", """
                {"reviewId": "%s", "userId": "%s", "albumId": "%s", "rating": %d, "oldRating": %d, "hasBody": false}
                """.formatted(review, user, albumId, rating, oldRating));
    }

    private String deleted(UUID eventId, UUID albumId, int rating) {
        return envelope(eventId, "ReviewDeleted", """
                {"reviewId": "%s", "userId": "%s", "albumId": "%s", "rating": %d}
                """.formatted(review, user, albumId, rating));
    }

    private static String envelope(UUID eventId, String eventType, String payload) {
        return """
                {"eventId": "%s", "eventType": "%s", "eventVersion": 1, "occurredAt": "2026-10-03T18:21:07Z",
                 "producer": "review-service", "payload": %s}
                """.formatted(eventId, eventType, payload);
    }
}
