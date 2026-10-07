package io.github.kerbiy.crate.review.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /reviews/albums/{albumId}}. Replaces my rating and text. */
record ReviewRequest(
        // Half stars: 1 = ½ star, 10 = 5 stars.
        @NotNull @Min(1) @Max(10) Integer rating,
        @Size(max = 5000) String body) {

    /** Blank text means "no text": stored as null. */
    String normalizedBody() {
        return body == null || body.isBlank() ? null : body.strip();
    }
}
