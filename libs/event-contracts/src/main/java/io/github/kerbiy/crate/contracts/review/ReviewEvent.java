package io.github.kerbiy.crate.contracts.review;

import java.util.UUID;

/**
 * Payload of a review.events message. Sealed: a {@code switch} over it must handle every event.
 * Ratings are half stars, 1–10, as stored by review-service.
 */
public sealed interface ReviewEvent permits ReviewCreated, ReviewUpdated, ReviewDeleted {

    UUID reviewId();

    UUID userId();

    /** Also the Kafka key. */
    UUID albumId();

    /** The envelope's eventType for this payload. */
    String eventType();
}
