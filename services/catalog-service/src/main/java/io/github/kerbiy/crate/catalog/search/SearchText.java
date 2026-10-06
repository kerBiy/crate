package io.github.kerbiy.crate.catalog.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one normalization used on both sides of a local search: for {@code albums.search_text} when
 * an album is stored, and for the user's query. Trigrams compare characters, so "Björk" and "bjork"
 * only match if both sides drop case and accents the same way.
 */
public final class SearchText {

    // Combining marks: what NFD splits off a letter, e.g. "ö" → "o" + U+0308.
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchText() {
    }

    /**
     * NFKC first folds look-alikes (full-width letters, ligatures, non-breaking spaces), NFD then
     * separates base letters from their accents so the accents can be removed.
     * "  Sigur  Rós " → "sigur ros".
     */
    public static String normalize(String text) {
        String folded = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC);
        String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD);
        String unaccented = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** What an album is found by: its title and the credited artists. */
    public static String forAlbum(String title, String artistCredit) {
        return normalize(title + " " + artistCredit);
    }
}
