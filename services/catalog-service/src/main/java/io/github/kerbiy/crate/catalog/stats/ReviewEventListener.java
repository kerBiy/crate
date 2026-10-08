package io.github.kerbiy.crate.catalog.stats;

import io.github.kerbiy.crate.contracts.review.ReviewCreated;
import io.github.kerbiy.crate.contracts.review.ReviewDeleted;
import io.github.kerbiy.crate.contracts.review.ReviewEvent;
import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import io.github.kerbiy.crate.contracts.review.ReviewUpdated;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads review.events (consumer group catalog-service, see application.yml) and hands each event to
 * {@link AlbumStatsService}.
 *
 * <p>Messages are plain JSON with no Java type header (SPEC 6.3): we read the envelope, look at
 * {@code eventType}, and only then pick the payload class. The producer's class names never matter.
 *
 * <p>Spring commits the record's offset after this method returns, so after the DB transaction
 * committed. If this throws, Spring's default error handler retries the record a few times and then
 * logs and skips it (Phase 2: retries with backoff and a dead-letter topic, SPEC 6.4).
 */
@Component
class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final AlbumStatsService stats;
    private final JsonMapper json;

    ReviewEventListener(AlbumStatsService stats, JsonMapper json) {
        this.stats = stats;
        this.json = json;
    }

    @KafkaListener(topics = ReviewEvents.TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        JsonNode envelope = json.readTree(record.value());
        String eventType = envelope.required("eventType").asString();
        Class<? extends ReviewEvent> payloadType = switch (eventType) {
            case ReviewEvents.CREATED -> ReviewCreated.class;
            case ReviewEvents.UPDATED -> ReviewUpdated.class;
            case ReviewEvents.DELETED -> ReviewDeleted.class;
            // A type added later (schema evolution is additive): not ours to handle, not an error.
            default -> null;
        };
        if (payloadType == null) {
            log.warn("Skipping unknown event type {} at {}-{}@{}", eventType, record.topic(), record.partition(),
                    record.offset());
            return;
        }
        UUID eventId = UUID.fromString(envelope.required("eventId").asString());
        ReviewEvent event = json.treeToValue(envelope.required("payload"), payloadType);
        stats.apply(eventId, event);
    }
}
