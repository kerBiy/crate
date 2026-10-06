package io.github.kerbiy.crate.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MusicBrainzQueryBuilderTest {

    private static final String TYPES = " AND primarytype:(album OR ep)";

    @Test
    void artistDashTitleSearchesEachField() {
        assertThat(MusicBrainzQueryBuilder.build("Radiohead - OK Computer"))
                .isEqualTo("artist:(Radiohead) AND releasegroup:(OK Computer)" + TYPES);
    }

    @Test
    void freeTextSearchesTitleOrArtist() {
        assertThat(MusicBrainzQueryBuilder.build("ok computer"))
                .isEqualTo("(releasegroup:(ok computer) OR artist:(ok computer))" + TYPES);
    }

    @Test
    void splitsOnFirstSeparatorOnly() {
        assertThat(MusicBrainzQueryBuilder.build("Wilco - Yankee Hotel Foxtrot - Deluxe"))
                .isEqualTo("artist:(Wilco) AND releasegroup:(Yankee Hotel Foxtrot \\- Deluxe)" + TYPES);
    }

    @Test
    void hyphenInsideAWordIsNotASeparator() {
        assertThat(MusicBrainzQueryBuilder.build("JAY-Z - Watch the Throne"))
                .isEqualTo("artist:(JAY\\-Z) AND releasegroup:(Watch the Throne)" + TYPES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Radiohead – OK Computer", "Radiohead — OK Computer", "  Radiohead \t -   OK Computer  "})
    void typographicDashesAndOddWhitespaceStillSplit(String input) {
        assertThat(MusicBrainzQueryBuilder.build(input))
                .isEqualTo("artist:(Radiohead) AND releasegroup:(OK Computer)" + TYPES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Radiohead -", "- OK Computer", " - "})
    void separatorWithAnEmptySideIsFreeText(String input) {
        String query = MusicBrainzQueryBuilder.build(input);
        assertThat(query).startsWith("(releasegroup:(").doesNotStartWith("artist:");
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
        assertThat(MusicBrainzQueryBuilder.build(input))
                .isEqualTo("(releasegroup:(" + escaped + ") OR artist:(" + escaped + "))" + TYPES);
    }

    @Test
    void escapesEveryOtherOperatorCharacter() {
        // Not in the table above: | is its delimiter.
        assertThat(MusicBrainzQueryBuilder.escape("a+b-c!d{e}f^g~h*i&&j||k"))
                .isEqualTo("a\\+b\\-c\\!d\\{e\\}f\\^g\\~h\\*i\\&\\&j\\|\\|k");
    }

    @Test
    void booleanOperatorWordsBecomePlainTerms() {
        assertThat(MusicBrainzQueryBuilder.build("Bob Dylan - Love AND Theft"))
                .isEqualTo("artist:(Bob Dylan) AND releasegroup:(Love and Theft)" + TYPES);
        assertThat(MusicBrainzQueryBuilder.escape("NOT OR ORANGE")).isEqualTo("not or ORANGE");
    }

    @Test
    void keepsCaseAndAccents() {
        assertThat(MusicBrainzQueryBuilder.build("Björk - Homogenic"))
                .isEqualTo("artist:(Björk) AND releasegroup:(Homogenic)" + TYPES);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsBlankInput(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> MusicBrainzQueryBuilder.build(input));
    }
}
