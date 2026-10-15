package io.github.kerbiy.crate.catalog.musicbrainz;

import static io.github.kerbiy.crate.catalog.musicbrainz.ListenPlatform.APPLE_MUSIC;
import static io.github.kerbiy.crate.catalog.musicbrainz.ListenPlatform.DEEZER;
import static io.github.kerbiy.crate.catalog.musicbrainz.ListenPlatform.SPOTIFY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListenLinkChoiceTest {

    @Test
    void theChosenReleasesOwnLinkWins() {
        ReleaseSummary chosen = release("2014-10-27", 13, link(SPOTIFY, "standard"));
        ReleaseSummary earlier = release("2014-10-01", 13, link(SPOTIFY, "earlier"));

        assertThat(ListenLinkChoice.choose(List.of(earlier, chosen), chosen))
                .containsExactly(link(SPOTIFY, "standard"));
    }

    @Test
    void otherwiseTheSameEditionBeatsADeluxeOneEvenIfLater() {
        // Like 1989: the CD carries no links, its digital copies and the deluxe edition do.
        ReleaseSummary cd = release("2014-10-27", 13);
        ReleaseSummary deluxe = release("2014-10-27", 19, link(SPOTIFY, "deluxe"), link(DEEZER, "deluxe"));
        ReleaseSummary digital = release("2015-01-01", 13, link(SPOTIFY, "standard"));

        // Spotify from the same edition; Deezer only has the deluxe one, which beats no link at all.
        assertThat(ListenLinkChoice.choose(List.of(deluxe, digital, cd), cd))
                .containsExactly(link(SPOTIFY, "standard"), link(DEEZER, "deluxe"));
    }

    @Test
    void amongEqualsTheEarliestThenTheMbid() {
        ReleaseSummary cd = release("1997-06-16", 12);
        ReleaseSummary later = release("2016-04-01", 12, link(APPLE_MUSIC, "later"));
        ReleaseSummary partialDate = release("2016", 12, link(APPLE_MUSIC, "partial"));
        ReleaseSummary first = release("2009-01-01", 12, link(APPLE_MUSIC, "first"));

        assertThat(ListenLinkChoice.choose(List.of(later, partialDate, first, cd), cd))
                .containsExactly(link(APPLE_MUSIC, "first"));
    }

    @Test
    void withoutAChosenReleaseAnyReleaseCounts() {
        ReleaseSummary only = release(null, 0, link(DEEZER, "only"), link(SPOTIFY, "only"));

        // Always in platform order, whatever order MusicBrainz listed them in.
        assertThat(ListenLinkChoice.choose(List.of(only), null))
                .containsExactly(link(SPOTIFY, "only"), link(DEEZER, "only"));
    }

    @Test
    void noLinksNoChoice() {
        assertThat(ListenLinkChoice.choose(List.of(release("1997", 12)), null)).isEmpty();
    }

    private static ListenLink link(ListenPlatform platform, String name) {
        return new ListenLink(platform, "https://example.com/" + platform + "/" + name);
    }

    private static ReleaseSummary release(String date, int tracks, ListenLink... links) {
        return new ReleaseSummary(UUID.randomUUID(), date, "XW", tracks, List.of(), List.of(links));
    }
}
