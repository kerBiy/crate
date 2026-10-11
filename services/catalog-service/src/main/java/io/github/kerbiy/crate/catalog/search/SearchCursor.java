package io.github.kerbiy.crate.catalog.search;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Where a page of search results ended: the last album's rank in the query's stored ranking
 * (search_results). Sent to clients as base64url of the rank; opaque to them, they only pass it back.
 * Ranks are unique within a query, so the rank alone is enough to continue after it.
 */
record SearchCursor(int afterRank) {

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(afterRank).getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException when the text isn't a cursor we made */
    static SearchCursor decode(String cursor) {
        // Base64 decoding and parseInt both throw IllegalArgumentException (NumberFormatException is one).
        int rank = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        if (rank < 1) {
            throw new IllegalArgumentException("Not a cursor: " + cursor);
        }
        return new SearchCursor(rank);
    }
}
