package io.github.kerbiy.crate.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MusicBrainzQueryBuilderTest {

    private static final String EXCLUDED = "secondarytype:(\"compilation\" OR \"live\" OR \"remix\" OR \"dj-mix\" "
            + "OR \"mixtape/street\" OR \"demo\" OR \"interview\" OR \"audiobook\" OR \"audio drama\")";

    /** {@code (T AND types AND NOT excluded) OR (T AND types AND "phrase")}, the shape of every search. */
    private static String expected(String terms, String phrase) {
        String required = "(" + terms + ") AND primarytype:(album OR ep)";
        return "(" + required + " AND NOT " + EXCLUDED + ") OR (" + required + " AND releasegroup:\"" + phrase + "\")";
    }

    private static String lucene(String input) {
        return MusicBrainzQueryBuilder.build(input, false).lucene();
    }

    @Test
    void freeTextRequiresEveryWordInTitleOrArtist() {
        assertThat(lucene("ok computer")).isEqualTo(expected(
                "(releasegroup:(ok) OR artist:(ok)) AND (releasegroup:(computer) OR artist:(computer))",
                "ok computer"));
    }

    @Test
    void artistDashTitleSearchesEachFieldWithEveryWordRequired() {
        MusicBrainzQueryBuilder.Query query = MusicBrainzQueryBuilder.build("Pink Floyd - The Wall", false);

        assertThat(query.lucene()).isEqualTo(expected(
                "artist:(Pink AND Floyd) AND releasegroup:(The AND Wall)", "The Wall"));
        assertThat(query.title()).isEqualTo("The Wall");
    }

    @Test
    void excludedSecondaryTypesComeBackOnlyThroughTheExactTitlePhrase() {
        MusicBrainzQueryBuilder.Query query = MusicBrainzQueryBuilder.build("at folsom prison", false);

        assertThat(query.lucene()).contains("AND NOT " + EXCLUDED).endsWith("AND releasegroup:\"at folsom prison\")");
        assertThat(query.title()).isEqualTo("at folsom prison");
        // Soundtracks are real albums (Help!, A Hard Day's Night): never excluded.
        assertThat(query.lucene()).doesNotContainIgnoringCase("soundtrack");
    }

    @Test
    void fuzzyAllowsMisspellingsInLongerWordsOnly() {
        assertThat(MusicBrainzQueryBuilder.build("radiohed kid a", true).lucene())
                .contains("(releasegroup:(radiohed~) OR artist:(radiohed~))")
                .contains("(releasegroup:(kid) OR artist:(kid))")
                .contains("(releasegroup:(a) OR artist:(a))")
                // The phrase is what was typed, not fuzzy.
                .endsWith("releasegroup:\"radiohed kid a\")");
        assertThat(MusicBrainzQueryBuilder.build("Radiohed - Amnesiak", true).lucene())
                .contains("artist:(Radiohed~) AND releasegroup:(Amnesiak~)");
    }

    @Test
    void artistAlbumsAreAlbumsOnlyWithoutExcludedTypes() {
        UUID radiohead = UUID.fromString("a74b1b7f-71a5-4011-9441-d0b5e4122711");

        assertThat(MusicBrainzQueryBuilder.artistAlbums(radiohead))
                .isEqualTo("arid:a74b1b7f-71a5-4011-9441-d0b5e4122711 AND primarytype:album AND NOT " + EXCLUDED);
    }

    @Test
    void wordsWithoutLettersOrDigitsAreNotRequired() {
        // MusicBrainz's index drops "&": as a required term it would match nothing.
        assertThat(lucene("Simon & Garfunkel")).isEqualTo(expected(
                "(releasegroup:(Simon) OR artist:(Simon)) AND (releasegroup:(Garfunkel) OR artist:(Garfunkel))",
                "Simon & Garfunkel"));
    }

    @Test
    void onlyPunctuationIsSearchedAsTyped() {
        assertThat(lucene("!!!")).isEqualTo(expected("releasegroup:(\\!\\!\\!)", "!!!"));
    }

    @Test
    void splitsOnFirstSeparatorOnly() {
        assertThat(MusicBrainzQueryBuilder.build("Wilco - Yankee Hotel Foxtrot - Deluxe", false).title())
                .isEqualTo("Yankee Hotel Foxtrot - Deluxe");
    }

    @Test
    void hyphenInsideAWordIsNotASeparator() {
        assertThat(lucene("JAY-Z - Watch the Throne")).startsWith("((artist:(JAY\\-Z) AND releasegroup:(Watch AND the AND Throne))");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Radiohead – OK Computer", "Radiohead — OK Computer", "  Radiohead \t -   OK Computer  "})
    void typographicDashesAndOddWhitespaceStillSplit(String input) {
        assertThat(lucene(input)).isEqualTo(expected("artist:(Radiohead) AND releasegroup:(OK AND Computer)", "OK Computer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Radiohead -", "- OK Computer", " - "})
    void separatorWithAnEmptySideIsFreeText(String input) {
        MusicBrainzQueryBuilder.Query query = MusicBrainzQueryBuilder.build(input, false);

        assertThat(query.lucene()).doesNotContain("artist:(Radiohead) AND", "artist:() AND");
        assertThat(query.title()).isEqualTo(MusicBrainzQueryBuilder.normalize(input));
    }

    @ParameterizedTest
    // Backtick as quote character, so the double quotes around Heroes are plain text.
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "AC/DC                  | AC\\/DC",
            "What's Going On?       | What's Going On\\?",
            "\"Heroes\"             | \\\"Heroes\\\"",
            "back\\slash            | back\\\\slash",
            "Live: 1975             | Live\\: 1975",
            "(What's the Story)     | \\(What's the Story\\)",
            "Sgt. Pepper's [Remix]  | Sgt. Pepper's \\[Remix\\]",
    })
    void escapesLuceneSpecialCharacters(String input, String escaped) {
        assertThat(MusicBrainzQueryBuilder.escape(input)).isEqualTo(escaped);
    }

    @Test
    void escapesEveryOtherOperatorCharacter() {
        // Not in the table above: | is its delimiter.
        assertThat(MusicBrainzQueryBuilder.escape("a+b-c!d{e}f^g~h*i&&j||k"))
                .isEqualTo("a\\+b\\-c\\!d\\{e\\}f\\^g\\~h\\*i\\&\\&j\\|\\|k");
    }

    @Test
    void phraseEscapesOnlyQuotesAndBackslashes() {
        assertThat(lucene("\"Heroes\" (Live)"))
                .contains("(releasegroup:(\\\"Heroes\\\") OR artist:(\\\"Heroes\\\"))")
                .endsWith("releasegroup:\"\\\"Heroes\\\" (Live)\")");
    }

    @Test
    void booleanOperatorWordsBecomePlainTerms() {
        assertThat(lucene("Bob Dylan - Love AND Theft")).contains("releasegroup:(Love AND and AND Theft)");
        assertThat(MusicBrainzQueryBuilder.escape("NOT OR ORANGE")).isEqualTo("not or ORANGE");
    }

    @Test
    void keepsCaseAndAccents() {
        assertThat(lucene("Björk - Homogenic")).isEqualTo(expected("artist:(Björk) AND releasegroup:(Homogenic)", "Homogenic"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsBlankInput(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> MusicBrainzQueryBuilder.build(input, false));
    }
}
