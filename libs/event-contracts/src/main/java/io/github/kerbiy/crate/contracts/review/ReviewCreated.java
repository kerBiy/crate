package io.github.kerbiy.crate.contracts.review;

import java.util.UUID;

/** Someone rated an album for the first time. */
public record ReviewCreated(UUID reviewId, UUID userId, UUID albumId, int rating, boolean hasBody)
        implements ReviewEvent {

    @Override
    public String eventType() {
        return ReviewEvents.CREATED;
    }
}
