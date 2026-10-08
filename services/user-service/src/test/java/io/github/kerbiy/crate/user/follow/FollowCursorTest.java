package io.github.kerbiy.crate.user.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowCursorTest {

    @Test
    void roundTripsWithMicroseconds() {
        FollowCursor cursor = new FollowCursor(Instant.parse("2026-10-08T12:00:00.123456Z"), UUID.randomUUID());

        assertThat(FollowCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void isUrlSafe() {
        FollowCursor cursor = new FollowCursor(Instant.now(), UUID.randomUUID());

        assertThat(cursor.encode()).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void rejectsTextThatIsNotACursor() {
        assertThatThrownBy(() -> FollowCursor.decode("not base64!")).isInstanceOf(IllegalArgumentException.class);
        String noBar = Base64.getUrlEncoder().encodeToString("hello".getBytes());
        assertThatThrownBy(() -> FollowCursor.decode(noBar)).isInstanceOf(IllegalArgumentException.class);
        String badDate = Base64.getUrlEncoder().encodeToString(("yesterday|" + UUID.randomUUID()).getBytes());
        assertThatThrownBy(() -> FollowCursor.decode(badDate)).isInstanceOf(IllegalArgumentException.class);
    }
}
