package io.github.kerbiy.crate.review.events;

import io.github.kerbiy.crate.contracts.EventEnvelope;
import io.github.kerbiy.crate.contracts.review.ReviewEvent;
import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends review events to Kafka once the transaction that produced them has committed.
 *
 * <p>ReviewService publishes a {@link ReviewEvent} as a Spring application event inside its
 * transaction. Spring holds it and calls this listener only after the commit, so a rolled-back
 * change is never announced. If the transaction rolls back, the event is simply dropped.
 *
 * <p>Known shortcut (SPEC 3.6, ADR-006): this is a dual write. If the process dies or Kafka is
 * unreachable right after the commit, the event is lost and catalog's album_stats drifts. The ERROR
 * below is the only trace. Phase 2 replaces this with a transactional outbox.
 */
@Component
class ReviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventPublisher.class);

    static final String PRODUCER = "review-service";

    private final KafkaTemplate<String, Object> kafka;

    ReviewEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterCommit(ReviewEvent event) {
        EventEnvelope<ReviewEvent> envelope = EventEnvelope.of(PRODUCER, event.eventType(), event);
        // Key = albumId: every event of one album goes to the same partition, so they stay in order.
        try {
            kafka.send(ReviewEvents.TOPIC, event.albumId().toString(), envelope)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            lost(envelope, error);
                        }
                    });
        } catch (RuntimeException e) {
            // send() itself throws when it can't even get topic metadata (max.block.ms).
            lost(envelope, e);
        }
    }

    private static void lost(EventEnvelope<ReviewEvent> envelope, Throwable error) {
        log.error("Lost {} {} for album {}: the review is saved but catalog won't count it (ADR-006)",
                envelope.eventType(), envelope.eventId(), envelope.payload().albumId(), error);
    }
}
