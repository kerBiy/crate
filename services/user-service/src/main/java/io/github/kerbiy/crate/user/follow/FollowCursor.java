package io.github.kerbiy.crate.user.follow;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Where a follow list page ended: the last row's (followedAt, userId). Sent to clients as base64url
 * of {@code followedAt|userId} (SPEC 7), opaque to them. The user id breaks ties between follows
 * made in the same microsecond. Same format as review-service's cursor, but its own code: services
 * share nothing but libs/event-contracts.
 */
record FollowCursor(Instant followedAt, UUID userId) {

    String encode() {
        String raw = followedAt + "|" + userId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException when the text isn't a cursor we made */
    static FollowCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            if (bar < 0) {
                throw new IllegalArgumentException("Not a cursor: " + cursor);
            }
            return new FollowCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Not a cursor: " + cursor, e);
        }
    }
}
