package io.github.kerbiy.crate.review.review;

import java.time.Instant;
import java.util.UUID;

record ReviewResponse(
        UUID id,
        UUID userId,
        UUID albumId,
        int rating,
        String body,
        Instant createdAt,
        Instant updatedAt) {

    static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getUserId(), review.getAlbumId(), review.getRating(),
                review.getBody(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
