package io.github.kerbiy.crate.catalog.musicbrainz;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns what a user typed into a MusicBrainz (Lucene) release-group query. The only place that
 * knows Lucene syntax.
 *
 * <ul>
 *   <li>{@code "Radiohead - OK Computer"} becomes
 *       {@code artist:(Radiohead) AND releasegroup:(OK Computer) AND primarytype:(album OR ep)}</li>
 *   <li>anything else is searched in both title and artist:
 *       {@code (releasegroup:(q) OR artist:(q)) AND primarytype:(album OR ep)}</li>
 * </ul>
 */
public final class MusicBrainzQueryBuilder {

    private static final String SEPARATOR = " - ";
    private static final String ALBUMS_AND_EPS = " AND primarytype:(album OR ep)";

    // Lucene reserves these; a backslash in front makes each one a literal character.
    private static final String SPECIAL_CHARACTERS = "+-&|!(){}[]^\"~*?:\\/";
    // Operators are case-sensitive; as lowercase words they are just search terms.
    private static final Set<String> OPERATORS = Set.of("AND", "OR", "NOT");

    // Hyphen, non-breaking hyphen, figure dash, en dash, em dash, horizontal bar, minus sign.
    private static final Pattern DASHES = Pattern.compile("[\\u2010-\\u2015\\u2212]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MusicBrainzQueryBuilder() {
    }

    public static String build(String input) {
        String query = normalize(input);
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        int split = query.indexOf(SEPARATOR);
        if (split > 0 && split + SEPARATOR.length() < query.length()) {
            String artist = query.substring(0, split);
            String title = query.substring(split + SEPARATOR.length());
            return "artist:(" + escape(artist) + ") AND releasegroup:(" + escape(title) + ")" + ALBUMS_AND_EPS;
        }
        String terms = escape(query);
        return "(releasegroup:(" + terms + ") OR artist:(" + terms + "))" + ALBUMS_AND_EPS;
    }

    /**
     * Folds look-alike characters (NFKC: full-width letters, non-breaking spaces), turns typographic
     * dashes into "-" so "Artist – Title" splits too, and collapses whitespace. Case and accents are
     * kept: MusicBrainz's index folds those itself.
     */
    static String normalize(String input) {
        String folded = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC);
        folded = DASHES.matcher(folded).replaceAll("-");
        return WHITESPACE.matcher(folded).replaceAll(" ").strip();
    }

    static String escape(String text) {
        return Arrays.stream(text.split(" "))
                .map(word -> OPERATORS.contains(word) ? word.toLowerCase() : escapeCharacters(word))
                .collect(Collectors.joining(" "));
    }

    private static String escapeCharacters(String word) {
        StringBuilder escaped = new StringBuilder(word.length());
        for (char c : word.toCharArray()) {
            if (SPECIAL_CHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
