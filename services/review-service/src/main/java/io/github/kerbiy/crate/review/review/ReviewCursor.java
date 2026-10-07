package io.github.kerbiy.crate.review.review;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Where a page ended: the last item's (createdAt, id). Sent to clients as base64url of
 * {@code createdAt|id} (SPEC 7). Opaque to them: they only pass it back.
 * The id breaks ties between reviews created in the same microsecond.
 */
record ReviewCursor(Instant createdAt, UUID id) {

    static ReviewCursor of(Review review) {
        return new ReviewCursor(review.getCreatedAt(), review.getId());
    }

    String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException when the text isn't a cursor we made */
    static ReviewCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            if (bar < 0) {
                throw new IllegalArgumentException("Not a cursor: " + cursor);
            }
            return new ReviewCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Not a cursor: " + cursor, e);
        }
    }
}
