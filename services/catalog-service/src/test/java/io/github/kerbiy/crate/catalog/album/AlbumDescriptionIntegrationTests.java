package io.github.kerbiy.crate.catalog.album;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code GET /albums/{id}/description}: the English Wikipedia intro, fetched lazily, then stored (ADR 014). */
class AlbumDescriptionIntegrationTests extends CatalogIntegrationTest {

    @Test
    void firstRequestFindsTheArticleAndLaterOnesAreLocal() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubUrlRels(album.id(), "url-rels-ok-computer.json");
        musicBrainz.stubFor(get(urlPathEqualTo(WIKIDATA)).withQueryParam("ids", equalTo("Q202996"))
                .willReturn(fixture("wikidata", "sitelinks-ok-computer.json")));
        musicBrainz.stubFor(get(urlPathEqualTo(WIKIPEDIA_SUMMARY + "OK_Computer"))
                .willReturn(fixture("wikipedia", "summary-ok-computer.json")));

        for (int i = 0; i < 2; i++) {
            client.get().uri("/albums/{id}/description", album.id()).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.text").value(String.class, text ->
                            assertThat(text).startsWith("OK Computer is the third studio album by the English rock band Radiohead"))
                    .jsonPath("$.articleUrl").isEqualTo("https://en.wikipedia.org/wiki/OK_Computer")
                    .jsonPath("$.articleTitle").isEqualTo("OK Computer");
        }

        // Three requests the first time (MusicBrainz, Wikidata, Wikipedia), none the second.
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH + "/" + album.id())));
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(WIKIDATA)));
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(WIKIPEDIA_SUMMARY + "OK_Computer")));
    }

    @Test
    void anAlbumWithoutAnEnglishArticleHasNoDescriptionUntilItIsCheckedAgain() {
        ReleaseGroup album = seed("Mugur de fluier", "Phoenix");
        stubUrlRels(album.id(), "url-rels-mugur-de-fluier.json");
        musicBrainz.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(fixture("wikidata", "sitelinks-mugur-de-fluier.json")));

        for (int i = 0; i < 2; i++) {
            client.get().uri("/albums/{id}/description", album.id()).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.text").isEmpty()
                    .jsonPath("$.articleUrl").isEmpty();
        }
        // "No article" is stored: the second view asked nobody, and Wikipedia was never needed.
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(WIKIDATA)));
        musicBrainz.verify(0, getRequestedFor(urlPathMatching(WIKIPEDIA_SUMMARY + ".*")));

        // A month later someone may have written it: ask again.
        jdbc.sql("update album_descriptions set fetched_at = now() - interval '31 days'").update();
        client.get().uri("/albums/{id}/description", album.id()).exchange().expectStatus().isOk();
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(WIKIDATA)));
    }

    @Test
    void anAlbumWithoutWikipediaLinksAsksNoWikimediaApi() {
        ReleaseGroup album = seed("Demo", "Nobody");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH + "/" + album.id())).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"relations\": []}")));

        client.get().uri("/albums/{id}/description", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.text").isEmpty();
        musicBrainz.verify(0, anyRequestedFor(urlPathMatching("/w[dp]/.*")));
        assertThat(count("album_descriptions")).isOne();
    }

    @Test
    void wikipediaDownIs503ForTheDescriptionOnly() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubUrlRels(album.id(), "url-rels-ok-computer.json");
        musicBrainz.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(fixture("wikidata", "sitelinks-ok-computer.json")));
        musicBrainz.stubFor(get(urlPathMatching(WIKIPEDIA_SUMMARY + ".*")).willReturn(serverError()));

        client.get().uri("/albums/{id}/description", album.id()).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:description-unavailable");

        // Nothing stored, so the next view asks again; the album itself is fine.
        assertThat(count("album_descriptions")).isZero();
        client.get().uri("/albums/{id}", album.id()).exchange().expectStatus().isOk();
    }

    @Test
    void musicBrainzDownIs503AndNothingIsStored() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH + "/" + album.id())).willReturn(serviceUnavailable()));

        client.get().uri("/albums/{id}/description", album.id()).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:description-unavailable");
        assertThat(count("album_descriptions")).isZero();
        musicBrainz.verify(0, anyRequestedFor(urlPathMatching("/w[dp]/.*")));
    }

    @Test
    void unknownAlbumIs404() {
        musicBrainz.stubFor(get(urlPathMatching(MB_SEARCH + "/.+")).willReturn(notFound()));

        client.get().uri("/albums/{id}/description", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:album-not-found");
        musicBrainz.verify(0, anyRequestedFor(urlPathMatching("/w[dp]/.*")));
    }

    private void stubUrlRels(UUID albumId, String fixture) {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH + "/" + albumId)).withQueryParam("inc", equalTo("url-rels"))
                .willReturn(fixture(fixture)));
    }
}
