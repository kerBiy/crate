package io.github.kerbiy.crate.contracts.review;

import java.util.UUID;

/**
 * Someone changed their rating or review text. {@code oldRating} lets consumers apply a delta
 * without knowing the previous state; it equals {@code rating} when only the text changed.
 */
public record ReviewUpdated(UUID reviewId, UUID userId, UUID albumId, int rating, int oldRating, boolean hasBody)
        implements ReviewEvent {

    @Override
    public String eventType() {
        return ReviewEvents.UPDATED;
    }
}
