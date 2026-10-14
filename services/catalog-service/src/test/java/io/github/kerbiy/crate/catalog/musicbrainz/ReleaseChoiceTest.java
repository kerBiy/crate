package io.github.kerbiy.crate.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rule for the album page's release (ADR 013). Real browses are in MusicBrainzClientTest. */
class ReleaseChoiceTest {

    @Test
    void nothingToChooseFrom() {
        assertThat(ReleaseChoice.choose(List.of())).isEmpty();
        assertThat(ReleaseChoice.choose(List.of(release("1997-05-21", 0, "EMI")))).isEmpty();
    }

    @Test
    void theStandardEditionBeatsAnEarlierBonusTrackEdition() {
        ReleaseSummary japanBonus = release("1997-05-21", 13, "EMI");
        ReleaseSummary europe = release("1997-06-16", 12, "Parlophone");
        ReleaseSummary us = release("1997-07-01", 12, "Capitol");

        assertThat(ReleaseChoice.choose(List.of(japanBonus, europe, us))).contains(europe);
    }

    @Test
    void onATieOfTrackCountsTheShorterEditionWins() {
        ReleaseSummary deluxe = release("1997-01-01", 14, "Parlophone");
        ReleaseSummary standard = release("1997-06-16", 12, "Parlophone");

        assertThat(ReleaseChoice.choose(List.of(deluxe, standard))).contains(standard);
    }

    @Test
    void theAlbumsUsualLabelBeatsAnEarlierImport() {
        // OK Computer: the Japanese EMI CD was out three weeks before the European Parlophone one.
        ReleaseSummary japan = release("1997-05-21", 12, "EMI");
        ReleaseSummary europe = release("1997-06-16", 12, "Parlophone");
        ReleaseSummary uk = release("1997-06-16", 12, "Parlophone");
        ReleaseSummary reissue = release("2009-03-24", 12, "Parlophone");

        assertThat(ReleaseChoice.choose(List.of(japan, reissue, uk, europe)))
                .contains(firstById(europe, uk));
    }

    @Test
    void aLabelCountsOncePerRelease() {
        // Two label-info entries naming Parlophone (two catalog numbers) are still one release.
        ReleaseSummary twice = new ReleaseSummary(UUID.randomUUID(), "1997-06-16", "XE", 12,
                List.of("Parlophone", "Parlophone"));
        ReleaseSummary emi1 = release("1997-05-21", 12, "EMI");
        ReleaseSummary emi2 = release("1998-01-01", 12, "EMI");

        assertThat(ReleaseChoice.choose(List.of(twice, emi1, emi2))).contains(emi1);
    }

    @Test
    void onATieOfLabelsTheEarliestReleasesLabelWins() {
        ReleaseSummary virgin = release("2001-03-12", 14, "Virgin");
        ReleaseSummary parlophone = release("2001-02-26", 14, "Parlophone");

        assertThat(ReleaseChoice.choose(List.of(virgin, parlophone))).contains(parlophone);
    }

    @Test
    void withoutAnyLabelTheEarliestWins() {
        ReleaseSummary later = release("1987", 14);
        ReleaseSummary earlier = release("1973", 14);

        assertThat(ReleaseChoice.choose(List.of(later, earlier))).contains(earlier);
    }

    @Test
    void aPartialDateSortsAfterTheFullDatesItContainsAndUnknownLast() {
        ReleaseSummary unknown = release(null, 10, "Reprise");
        ReleaseSummary year = release("1971", 10, "Reprise");
        ReleaseSummary month = release("1971-06", 10, "Reprise");
        ReleaseSummary day = release("1971-06-22", 10, "Reprise");

        assertThat(ReleaseChoice.choose(List.of(unknown, year, month, day))).contains(day);
        assertThat(ReleaseChoice.choose(List.of(unknown, year, month))).contains(month);
        assertThat(ReleaseChoice.choose(List.of(unknown, year))).contains(year);
        assertThat(ReleaseChoice.sortableDate("1971")).isLessThan(ReleaseChoice.sortableDate("1972-01-01"));
    }

    @Test
    void theSameDateIsSettledByMbidWhateverTheOrder() {
        ReleaseSummary a = release("1977-02-04", 11, "Warner Bros. Records");
        ReleaseSummary b = release("1977-02-04", 11, "Warner Bros. Records");

        assertThat(ReleaseChoice.choose(List.of(a, b))).isEqualTo(ReleaseChoice.choose(List.of(b, a)))
                .contains(firstById(a, b));
    }

    private static ReleaseSummary release(String date, int tracks, String... labels) {
        return new ReleaseSummary(UUID.randomUUID(), date, "XE", tracks, List.of(labels));
    }

    private static ReleaseSummary firstById(ReleaseSummary a, ReleaseSummary b) {
        return a.id().toString().compareTo(b.id().toString()) < 0 ? a : b;
    }
}
