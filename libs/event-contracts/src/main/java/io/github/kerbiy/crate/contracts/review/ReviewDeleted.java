package io.github.kerbiy.crate.contracts.review;

import java.util.UUID;

/** Someone removed their rating (and its text). {@code rating} is what was removed. */
public record ReviewDeleted(UUID reviewId, UUID userId, UUID albumId, int rating) implements ReviewEvent {

    @Override
    public String eventType() {
        return ReviewEvents.DELETED;
    }
}
