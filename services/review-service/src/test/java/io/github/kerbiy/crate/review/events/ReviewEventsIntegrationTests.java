package io.github.kerbiy.crate.review.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import io.github.kerbiy.crate.review.ReviewIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What review-service publishes to review.events, and when (SPEC 5.4, 6). */
class ReviewEventsIntegrationTests extends ReviewIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private final UUID me = UUID.randomUUID();
    private final UUID album = UUID.randomUUID();
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    KafkaContainer kafka;

    private TopicReader topic;

    @BeforeEach
    void openTopic() {
        topic = new TopicReader(kafka.getBootstrapServers(), ReviewEvents.TOPIC);
    }

    @AfterEach
    void closeTopic() {
        topic.close();
    }

    @Test
    void firstRatingPublishesReviewCreatedAfterCommit() {
        put(me, album, 8, "Great").expectStatus().isCreated();

        ConsumerRecord<String, String> record = topic.await(this::ofMyAlbum, WAIT);

        assertThat(record.key()).isEqualTo(album.toString());
        // No Java type header: consumers mustn't depend on our class names (SPEC 6.3).
        assertThat(record.headers().toArray()).isEmpty();
        JsonNode event = json.readTree(record.value());
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.get("eventType").asString()).isEqualTo("ReviewCreated");
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.get("producer").asString()).isEqualTo("review-service");
        // An ISO-8601 string, not a number.
        assertThat(event.get("occurredAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        JsonNode payload = event.get("payload");
        String reviewId = jdbc.sql("select id from reviews").query(String.class).single();
        assertThat(payload.get("reviewId").asString()).isEqualTo(reviewId);
        assertThat(payload.get("userId").asString()).isEqualTo(me.toString());
        assertThat(payload.get("albumId").asString()).isEqualTo(album.toString());
        assertThat(payload.get("rating").asInt()).isEqualTo(8);
        assertThat(payload.get("hasBody").asBoolean()).isTrue();
    }

    @Test
    void changingTheRatingPublishesReviewUpdatedWithTheOldRating() {
        put(me, album, 4, null).expectStatus().isCreated();
        put(me, album, 9, null).expectStatus().isOk();

        JsonNode payload = payload(topic.await(type("ReviewUpdated"), WAIT));

        assertThat(payload.get("rating").asInt()).isEqualTo(9);
        assertThat(payload.get("oldRating").asInt()).isEqualTo(4);
        assertThat(payload.get("hasBody").asBoolean()).isFalse();
    }

    @Test
    void repeatingTheSamePutPublishesNothingNew() {
        put(me, album, 7, "Same").expectStatus().isCreated();
        put(me, album, 7, "Same").expectStatus().isOk();
        // A later real change, on the same key (same partition, so it arrives after anything before it).
        put(me, album, 6, "Same").expectStatus().isOk();

        topic.await(type("ReviewUpdated"), WAIT);

        assertThat(topic.seen()).filteredOn(this::ofMyAlbum)
                .extracting(record -> json.readTree(record.value()).get("eventType").asString())
                .containsExactly("ReviewCreated", "ReviewUpdated");
    }

    @Test
    void deletingPublishesReviewDeletedWithTheRemovedRatingOnce() {
        put(me, album, 3, null).expectStatus().isCreated();
        delete(me);
        delete(me);
        put(me, album, 5, null).expectStatus().isCreated();

        // The second ReviewCreated comes after everything above on this key.
        topic.await(record -> ofMyAlbum(record) && payload(record).get("rating").asInt() == 5, WAIT);

        assertThat(topic.seen()).filteredOn(this::ofMyAlbum)
                .extracting(record -> json.readTree(record.value()).get("eventType").asString())
                .containsExactly("ReviewCreated", "ReviewDeleted", "ReviewCreated");
        JsonNode deleted = payload(topic.await(type("ReviewDeleted"), WAIT));
        assertThat(deleted.get("rating").asInt()).isEqualTo(3);
    }

    @Test
    void rolledBackChangePublishesNothing() {
        // FailingCommit throws just before the commit, so the transaction rolls back.
        put(FailingCommit.USER, album, 2, null).expectStatus().is5xxServerError();
        assertThat(countReviews()).isZero();

        // Someone else rates the same album: same key, same partition, published after the failed one
        // would have been. If the rolled-back event had gone out, it would be read first.
        put(me, album, 9, null).expectStatus().isCreated();
        topic.await(this::ofMyAlbum, WAIT);

        assertThat(topic.seen()).filteredOn(this::ofMyAlbum).hasSize(1)
                .first().satisfies(record -> assertThat(payload(record).get("userId").asString())
                        .isEqualTo(me.toString()));
    }

    @Test
    void topicExistsWithThreePartitions() throws Exception {
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            TopicDescription description = admin.describeTopics(List.of(ReviewEvents.TOPIC))
                    .allTopicNames().get().get(ReviewEvents.TOPIC);
            assertThat(description.partitions()).hasSize(3);
            assertThat(description.partitions().getFirst().replicas()).hasSize(1);
        }
    }

    private boolean ofMyAlbum(ConsumerRecord<String, String> record) {
        return album.toString().equals(record.key());
    }

    private Predicate<ConsumerRecord<String, String>> type(String eventType) {
        return record -> ofMyAlbum(record)
                && eventType.equals(json.readTree(record.value()).get("eventType").asString());
    }

    private JsonNode payload(ConsumerRecord<String, String> record) {
        return json.readTree(record.value()).get("payload");
    }

    private void delete(UUID userId) {
        client.delete().uri("/reviews/albums/{albumId}", album).header("X-User-Id", userId.toString()).exchange()
                .expectStatus().isNoContent();
    }
}
