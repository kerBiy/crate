package io.github.kerbiy.crate.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SearchCursorTest {

    @Test
    void roundTrips() {
        String encoded = new SearchCursor(12).encode();

        assertThat(SearchCursor.decode(encoded)).isEqualTo(new SearchCursor(12));
        // URL-safe as it is: no padding, nothing to escape.
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void rejectsWhatItDidNotMake() {
        assertThatIllegalArgumentException().isThrownBy(() -> SearchCursor.decode("not base64!"));
        assertThatIllegalArgumentException().isThrownBy(() -> SearchCursor.decode(base64("twelve")));
        assertThatIllegalArgumentException().isThrownBy(() -> SearchCursor.decode(base64("0")));
        assertThatIllegalArgumentException().isThrownBy(() -> SearchCursor.decode(base64("-1")));
    }

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
