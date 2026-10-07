package io.github.kerbiy.crate.review.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewCursorTest {

    @Test
    void roundTripsWithMicrosecondPrecision() {
        ReviewCursor cursor = new ReviewCursor(Instant.parse("2026-10-07T18:21:07.123456Z"), UUID.randomUUID());

        assertThat(ReviewCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void isUrlSafe() {
        String encoded = new ReviewCursor(Instant.now(), UUID.randomUUID()).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void rejectsAnythingElse() {
        for (String garbage : new String[] {"not-a-cursor", "%%%", base64("2026|nope"), base64("no bar here"),
                base64("yesterday|" + UUID.randomUUID())}) {
            assertThatIllegalArgumentException().isThrownBy(() -> ReviewCursor.decode(garbage));
        }
    }

    private static String base64(String raw) {
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
