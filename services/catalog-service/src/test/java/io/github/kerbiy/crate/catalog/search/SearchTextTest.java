package io.github.kerbiy.crate.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void lowercasesAndStripsDiacritics() {
        assertThat(SearchText.normalize("Björk")).isEqualTo("bjork");
        // "æ" is a letter of its own, not "a" plus an accent, so it stays.
        assertThat(SearchText.normalize("Sigur Rós – Ágætis byrjun")).isEqualTo("sigur ros – agætis byrjun");
    }

    @Test
    void collapsesWhitespace() {
        assertThat(SearchText.normalize("  OK \t Computer  ")).isEqualTo("ok computer");
    }

    @Test
    void foldsLookAlikeCharacters() {
        // Full-width letters, as some mobile keyboards produce.
        assertThat(SearchText.normalize("ＲＡＤＩＯＨＥＡＤ")).isEqualTo("radiohead");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(SearchText.normalize(null)).isEmpty();
        assertThat(SearchText.normalize(" ́ ")).isEmpty();
    }

    @Test
    void albumSearchTextHoldsTitleAndArtist() {
        assertThat(SearchText.forAlbum("Homogenic", "Björk")).isEqualTo("homogenic bjork");
    }
}
