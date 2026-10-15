package io.github.kerbiy.crate.catalog.album;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /albums/{id}/tracks}: fetched lazily from MusicBrainz, then stored (ADR 013), with the
 * album's listening links from the same browse (ADR 015).
 */
class AlbumTracksIntegrationTests extends CatalogIntegrationTest {

    private static final String MB_RELEASES = "/ws/2/release";
    private static final String OK_COMPUTER_EUROPE = "425d8db9-88a3-31e2-bd23-b1a967626dd5";
    private static final String MELLON_COLLIE_UK = "15cf95c2-bbe6-37cc-8a61-c5d5116b267a";

    @Test
    void firstRequestFetchesTheChosenReleaseAndLaterOnesAreLocal() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubBrowse("browse-releases-ok-computer.json");
        stubRelease(OK_COMPUTER_EUROPE, "release-ok-computer.json");

        for (int i = 0; i < 2; i++) {
            client.get().uri("/albums/{id}/tracks", album.id()).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.releaseId").isEqualTo(OK_COMPUTER_EUROPE)
                    .jsonPath("$.label").isEqualTo("Parlophone")
                    .jsonPath("$.trackCount").isEqualTo(12)
                    .jsonPath("$.lengthMs").isEqualTo(3207156)
                    .jsonPath("$.genres[0]").isEqualTo("alternative rock")
                    .jsonPath("$.genres.length()").isEqualTo(5)
                    .jsonPath("$.discs.length()").isEqualTo(1)
                    .jsonPath("$.discs[0].position").isEqualTo(1)
                    .jsonPath("$.discs[0].tracks.length()").isEqualTo(12)
                    .jsonPath("$.discs[0].tracks[0].position").isEqualTo(1)
                    .jsonPath("$.discs[0].tracks[0].number").isEqualTo("1")
                    .jsonPath("$.discs[0].tracks[0].title").isEqualTo("Airbag")
                    .jsonPath("$.discs[0].tracks[0].lengthMs").isEqualTo(284400)
                    .jsonPath("$.discs[0].tracks[11].title").isEqualTo("The Tourist")
                    // The European CD has no streaming links; they come from its 12-track digital copy.
                    .jsonPath("$.listenLinks.length()").isEqualTo(6)
                    .jsonPath("$.listenLinks[0].platform").isEqualTo("SPOTIFY")
                    .jsonPath("$.listenLinks[0].url").isEqualTo("https://open.spotify.com/album/6dVIqQ8qmQ5GBnJ9shOYGE")
                    .jsonPath("$.listenLinks[1].platform").isEqualTo("APPLE_MUSIC")
                    .jsonPath("$.listenLinks[2].platform").isEqualTo("YOUTUBE_MUSIC")
                    .jsonPath("$.listenLinks[2].url")
                    .isEqualTo("https://music.youtube.com/playlist?list=OLAK5uy_nc6afxSnmK8OFML8dF9q_0D1MBMXsGnL4")
                    .jsonPath("$.listenLinks[5].platform").isEqualTo("BANDCAMP");
        }
        assertThat(count("album_listen_links")).isEqualTo(6);

        // Two MusicBrainz requests the first time (browse, lookup), none the second.
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_RELEASES)));
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_RELEASES + "/" + OK_COMPUTER_EUROPE)));
    }

    @Test
    void albumDetailsCarryTheReleaseFactsOnceFetched() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.release").doesNotExist()
                .jsonPath("$.genres.length()").isEqualTo(0);

        stubBrowse("browse-releases-ok-computer.json");
        stubRelease(OK_COMPUTER_EUROPE, "release-ok-computer.json");
        client.get().uri("/albums/{id}/tracks", album.id()).exchange().expectStatus().isOk();

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.release.label").isEqualTo("Parlophone")
                .jsonPath("$.release.trackCount").isEqualTo(12)
                .jsonPath("$.release.lengthMs").isEqualTo(3207156)
                .jsonPath("$.genres[0]").isEqualTo("alternative rock");
    }

    @Test
    void everyDiscIsServedInOrder() {
        ReleaseGroup album = seed("Mellon Collie and the Infinite Sadness", "The Smashing Pumpkins");
        stubBrowse("browse-releases-mellon-collie.json");
        stubRelease(MELLON_COLLIE_UK, "release-mellon-collie.json");

        client.get().uri("/albums/{id}/tracks", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.label").isEqualTo("Virgin")
                .jsonPath("$.trackCount").isEqualTo(28)
                .jsonPath("$.discs.length()").isEqualTo(2)
                .jsonPath("$.discs[1].position").isEqualTo(2)
                .jsonPath("$.discs[1].tracks.length()").isEqualTo(14)
                .jsonPath("$.discs[1].tracks[0].position").isEqualTo(1)
                .jsonPath("$.discs[1].tracks[0].title").isEqualTo("Where Boys Fear to Tread")
                // Two 28-track digital releases on the same day: the lower MBID's Apple link, the other's
                // Spotify, Deezer and Tidal. The 42-track 2025 deluxe edition's links lose.
                .jsonPath("$.listenLinks[0].url").isEqualTo("https://open.spotify.com/album/4bPT6Q8ppaSNppk1kbEbLl")
                .jsonPath("$.listenLinks[1].url").isEqualTo("https://music.apple.com/us/album/721224313")
                .jsonPath("$.listenLinks[2].url").isEqualTo("https://www.deezer.com/album/6158273")
                .jsonPath("$.listenLinks[3].url").isEqualTo("https://tidal.com/album/18306486");
    }

    @Test
    void musicBrainzDownIs503ForTheTracklistOnly() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        musicBrainz.stubFor(get(urlPathMatching(MB_RELEASES + ".*")).willReturn(serviceUnavailable()));

        client.get().uri("/albums/{id}/tracks", album.id()).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:tracks-unavailable");

        // Retried like every MusicBrainz request, nothing stored, and the album itself is fine.
        musicBrainz.verify(3, getRequestedFor(urlPathEqualTo(MB_RELEASES)));
        assertThat(count("album_releases")).isZero();
        client.get().uri("/albums/{id}", album.id()).exchange().expectStatus().isOk();
    }

    @Test
    void anAlbumWithoutReleasesHasAnEmptyTracklistUntilItIsCheckedAgain() {
        ReleaseGroup album = seed("Demo", "Nobody");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_RELEASES)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"release-count\": 0, \"releases\": []}")));

        client.get().uri("/albums/{id}/tracks", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.releaseId").doesNotExist()
                .jsonPath("$.discs.length()").isEqualTo(0)
                .jsonPath("$.listenLinks.length()").isEqualTo(0);
        // Official first, then every status: two requests. Stored, so the next request asks nobody.
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_RELEASES)));
        client.get().uri("/albums/{id}/tracks", album.id()).exchange().expectStatus().isOk();
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_RELEASES)));

        // A month later MusicBrainz may know a release: ask again.
        jdbc.sql("update album_releases set fetched_at = now() - interval '31 days'").update();
        client.get().uri("/albums/{id}/tracks", album.id()).exchange().expectStatus().isOk();
        musicBrainz.verify(4, getRequestedFor(urlPathEqualTo(MB_RELEASES)));
    }

    @Test
    void unknownAlbumIs404() {
        musicBrainz.stubFor(get(urlPathMatching("/ws/2/release-group/.+")).willReturn(notFound()));

        client.get().uri("/albums/{id}/tracks", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:album-not-found");
        musicBrainz.verify(0, getRequestedFor(urlPathEqualTo(MB_RELEASES)));
    }

    private void stubBrowse(String fixture) {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_RELEASES)).willReturn(fixture(fixture)));
    }

    private void stubRelease(String releaseId, String fixture) {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_RELEASES + "/" + releaseId)).willReturn(fixture(fixture)));
    }
}
