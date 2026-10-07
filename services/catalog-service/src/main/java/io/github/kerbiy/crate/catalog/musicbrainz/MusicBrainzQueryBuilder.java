package io.github.kerbiy.crate.catalog.musicbrainz;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns what a user typed into a MusicBrainz (Lucene) release-group query. The only place that
 * knows Lucene syntax. See docs/adr/010-search-ranking.md for why the query looks like this.
 *
 * <ul>
 *   <li>Every word is required (AND), and each word may match the title or the artist:
 *       {@code kind of blue} becomes {@code (releasegroup:(kind) OR artist:(kind)) AND (… of …) AND (… blue …)}.
 *       Lucene's default would OR them, and "of" alone matches a quarter of a million albums.</li>
 *   <li>{@code "Radiohead - OK Computer"} pins the left side to the artist and the right to the title.</li>
 *   <li>Compilations, live albums, remixes etc. are excluded, unless the title is exactly the phrase
 *       that was typed, so "at folsom prison" still finds the live album. The phrase match is
 *       "contains"; {@link MusicBrainzClient} keeps only exact titles from that branch.</li>
 * </ul>
 */
public final class MusicBrainzQueryBuilder {

    /**
     * Secondary types hidden from search by default, as MusicBrainz spells them. Soundtracks stay:
     * plenty of real albums are soundtracks (Help!, A Hard Day's Night).
     */
    public static final List<String> EXCLUDED_SECONDARY_TYPES = List.of(
            "Compilation", "Live", "Remix", "DJ-mix", "Mixtape/Street", "Demo", "Interview", "Audiobook",
            "Audio drama");

    private static final String SEPARATOR = " - ";
    private static final String ALBUMS_AND_EPS = "primarytype:(album OR ep)";
    // Quoted, because "dj-mix" and "mixtape/street" contain characters Lucene would otherwise parse.
    private static final String EXCLUDED = "secondarytype:(" + EXCLUDED_SECONDARY_TYPES.stream()
            .map(type -> "\"" + type.toLowerCase(Locale.ROOT) + "\"")
            .collect(Collectors.joining(" OR ")) + ")";
    // Shorter words would match too much once misspellings are allowed ("kid~" → "kiss", "kit", ...).
    private static final int MIN_FUZZY_LENGTH = 4;

    // Lucene reserves these; a backslash in front makes each one a literal character.
    private static final String SPECIAL_CHARACTERS = "+-&|!(){}[]^\"~*?:\\/";
    // Operators are case-sensitive; as lowercase words they are just search terms.
    private static final Set<String> OPERATORS = Set.of("AND", "OR", "NOT");

    // Hyphen, non-breaking hyphen, figure dash, en dash, em dash, horizontal bar, minus sign.
    private static final Pattern DASHES = Pattern.compile("[\\u2010-\\u2015\\u2212]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private MusicBrainzQueryBuilder() {
    }

    /**
     * @param lucene the query to send
     * @param title  the part of the input that is a title: an excluded secondary type is kept only
     *               when the release group's title is exactly this
     */
    public record Query(String lucene, String title) {
    }

    /**
     * @param fuzzy allow small misspellings in longer words ({@code radiohed~}); used as a second
     *              try when the exact query finds nothing
     */
    public static Query build(String input, boolean fuzzy) {
        String query = normalize(input);
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        int split = query.indexOf(SEPARATOR);
        if (split > 0 && split + SEPARATOR.length() < query.length()) {
            String artist = query.substring(0, split);
            String title = query.substring(split + SEPARATOR.length());
            String terms = "artist:(" + allOf(artist, fuzzy) + ") AND releasegroup:(" + allOf(title, fuzzy) + ")";
            return new Query(excludingSecondaryTypes(terms, title), title);
        }
        String terms = words(query)
                .map(word -> fuzzy(escapeWord(word), word, fuzzy))
                .map(term -> "(releasegroup:(" + term + ") OR artist:(" + term + "))")
                .collect(Collectors.joining(" AND "));
        if (terms.isEmpty()) {
            // Only punctuation, e.g. "!!!": nothing to require word by word, so search it as typed.
            terms = "releasegroup:(" + escape(query) + ")";
        }
        return new Query(excludingSecondaryTypes(terms, query), query);
    }

    /** The artist's own albums: no EPs, nothing excluded, whatever their titles. */
    public static String artistAlbums(UUID artistId) {
        return "arid:" + artistId + " AND primarytype:album AND NOT " + EXCLUDED;
    }

    /**
     * Lucene can't OR a pure negative ("not live, or titled exactly …" matches nothing), so the
     * required terms are repeated in each branch: {@code (T AND NOT excluded) OR (T AND "phrase")}.
     */
    private static String excludingSecondaryTypes(String terms, String phrase) {
        String required = "(" + terms + ") AND " + ALBUMS_AND_EPS;
        return "(" + required + " AND NOT " + EXCLUDED + ") OR (" + required + " AND releasegroup:\"" + escapePhrase(phrase) + "\")";
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
        return Arrays.stream(text.split(" ")).map(MusicBrainzQueryBuilder::escapeWord).collect(Collectors.joining(" "));
    }

    /** {@code pink AND floyd}: inside a field, too, every word is required. */
    private static String allOf(String text, boolean fuzzy) {
        String terms = words(text).map(word -> fuzzy(escapeWord(word), word, fuzzy)).collect(Collectors.joining(" AND "));
        return terms.isEmpty() ? escape(text) : terms;
    }

    /**
     * Words with a letter or digit. A lone "&" or "-" would become a required term MusicBrainz's
     * index never contains (it drops punctuation), and "Simon & Garfunkel" would find nothing.
     */
    private static Stream<String> words(String text) {
        return Arrays.stream(text.split(" ")).filter(word -> LETTER_OR_DIGIT.matcher(word).find());
    }

    private static String fuzzy(String term, String word, boolean fuzzy) {
        return fuzzy && word.length() >= MIN_FUZZY_LENGTH ? term + "~" : term;
    }

    private static String escapeWord(String word) {
        if (OPERATORS.contains(word)) {
            return word.toLowerCase(Locale.ROOT);
        }
        StringBuilder escaped = new StringBuilder(word.length());
        for (char c : word.toCharArray()) {
            if (SPECIAL_CHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /** Inside quotes only " and \ are special. */
    private static String escapePhrase(String phrase) {
        return phrase.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
