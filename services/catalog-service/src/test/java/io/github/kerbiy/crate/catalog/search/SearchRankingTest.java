package io.github.kerbiy.crate.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.musicbrainz.ArtistCredit;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.musicbrainz.SearchHit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchRankingTest {

    private static final double WEIGHT = 15;
    private static final ArtistCredit RADIOHEAD = artist("Radiohead");
    private static final ArtistCredit BJORK = artist("Björk");
    private static final ArtistCredit BEATLES = artist("The Beatles");

    // --- which artist the query names ---

    @Test
    void wholeQueryNamingACreditedArtistMatches() {
        List<SearchHit> hits = List.of(hit(album("Radiohead", 1, BJORK), 100), hit(album("Creep", 4, RADIOHEAD), 46));

        assertThat(SearchRanking.matchingArtist("radiohead", hits)).contains(RADIOHEAD.id());
    }

    @Test
    void accentsCaseAndALeadingTheDoNotMatter() {
        assertThat(SearchRanking.matchingArtist("bjork", List.of(hit(album("Post", 31, BJORK), 90))))
                .contains(BJORK.id());
        assertThat(SearchRanking.matchingArtist("beatles", List.of(hit(album("Help!", 94, BEATLES), 90))))
                .contains(BEATLES.id());
        assertThat(SearchRanking.matchingArtist("the beatles", List.of(hit(album("Help!", 94, BEATLES), 90))))
                .contains(BEATLES.id());
    }

    @Test
    void artistPlusTitleIsNotAnArtistQuery() {
        assertThat(SearchRanking.matchingArtist("radiohead kid a", List.of(hit(album("Kid A", 30, RADIOHEAD), 100))))
                .isEmpty();
    }

    @Test
    void nameAsCreditedCountsForASingleArtist() {
        ArtistCredit ye = artist("Ye");
        ReleaseGroup credited = new ReleaseGroup(UUID.randomUUID(), "Graduation", "Kanye West", "Album", List.of(),
                "2007", 50, List.of(ye));

        assertThat(SearchRanking.matchingArtist("kanye west", List.of(hit(credited, 90)))).contains(ye.id());
    }

    // --- order ---

    @Test
    void popularityLiftsTheClassicAboveATitleTwin() {
        // Rumours (EP) by the band Rumours scores 100 with 1 release; Fleetwood Mac's has 81.
        ReleaseGroup twin = album("Rumours", 1, artist("Rumours"));
        ReleaseGroup classic = album("Rumours", 81, artist("Fleetwood Mac"));

        assertThat(SearchRanking.merge(List.of(), List.of(hit(twin, 100), hit(classic, 90)), WEIGHT))
                .containsExactly(classic, twin);
    }

    @Test
    void scoreStillWinsWhenPopularityIsClose() {
        ReleaseGroup exact = album("Kid A", 30, RADIOHEAD);
        ReleaseGroup partial = album("Kid A Mnesia", 40, RADIOHEAD);

        assertThat(SearchRanking.merge(List.of(), List.of(hit(partial, 70), hit(exact, 100)), WEIGHT))
                .containsExactly(exact, partial);
    }

    @Test
    void artistAlbumsComeFirstMostReleasedFirstThenTheRestWithoutDuplicates() {
        ReleaseGroup okComputer = album("OK Computer", 39, RADIOHEAD);
        ReleaseGroup kidA = album("Kid A", 30, RADIOHEAD);
        ReleaseGroup selfTitledEp = album("Radiohead", 1, RADIOHEAD);
        ReleaseGroup tribute = album("Radiohead", 1, artist("Wesley Willis"));

        List<ReleaseGroup> ranked = SearchRanking.merge(List.of(kidA, okComputer),
                List.of(hit(selfTitledEp, 100), hit(kidA, 50), hit(tribute, 64)), WEIGHT);

        assertThat(ranked).containsExactly(okComputer, kidA, selfTitledEp, tribute);
    }

    @Test
    void unknownReleaseCountCountsAsNone() {
        ReleaseGroup unknown = new ReleaseGroup(UUID.randomUUID(), "X", "A", "Album", List.of(), null, null, List.of());
        ReleaseGroup one = album("Y", 1, artist("B"));

        assertThat(SearchRanking.merge(List.of(), List.of(hit(unknown, 80), hit(one, 80)), WEIGHT))
                .containsExactly(one, unknown);
    }

    private static ArtistCredit artist(String name) {
        return new ArtistCredit(UUID.randomUUID(), name, name);
    }

    private static ReleaseGroup album(String title, int releases, ArtistCredit artist) {
        return new ReleaseGroup(UUID.randomUUID(), title, artist.name(), "Album", List.of(), "1997", releases,
                List.of(artist));
    }

    private static SearchHit hit(ReleaseGroup album, int score) {
        return new SearchHit(album, score);
    }
}
