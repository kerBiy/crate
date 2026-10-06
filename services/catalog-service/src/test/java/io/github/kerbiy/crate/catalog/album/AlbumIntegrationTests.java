package io.github.kerbiy.crate.catalog.album;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AlbumIntegrationTests extends CatalogIntegrationTest {

    private static final String OK_COMPUTER = "b1392450-e666-3926-a536-22c65f834433";
    private static final String RADIOHEAD = "a74b1b7f-71a5-4011-9441-d0b5e4122711";
    private static final String CREEP_SINGLE = "c5bc370b-95c2-3634-bb89-51bb2dce97c3";
    private static final String MB_LOOKUP = "/ws/2/release-group/.+";

    // --- GET /albums/{id} ---

    @Test
    void storedAlbumIsServedWithoutMusicBrainz() {
        ReleaseGroup album = seed("Homogenic", "Björk");

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(album.id().toString())
                .jsonPath("$.title").isEqualTo("Homogenic")
                .jsonPath("$.artistCredit").isEqualTo("Björk")
                .jsonPath("$.primaryArtistId").isEqualTo(album.artists().getFirst().id().toString())
                .jsonPath("$.primaryType").isEqualTo("Album")
                .jsonPath("$.firstReleaseDate").isEqualTo("1997-05-21")
                .jsonPath("$.year").isEqualTo("1997")
                .jsonPath("$.coverUrl").isEqualTo(
                        "https://coverartarchive.org/release-group/" + album.id() + "/front-500")
                .jsonPath("$.ratingCount").isEqualTo(0)
                .jsonPath("$.avgRating").doesNotExist();

        musicBrainz.verify(0, getRequestedFor(urlPathMatching(MB_LOOKUP)));
    }

    @Test
    void ratingStatsAreIncluded() {
        ReleaseGroup album = seed("Homogenic", "Björk");
        // 4.5 and 4 stars, stored as half-star steps 9 and 8.
        jdbc.sql("insert into album_stats (album_id, rating_count, rating_sum) values (:id, 2, 17)")
                .param("id", album.id()).update();

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ratingCount").isEqualTo(2)
                .jsonPath("$.avgRating").isEqualTo(4.3);
    }

    @Test
    void unknownAlbumIsFetchedFromMusicBrainzAndStored() {
        musicBrainz.stubFor(get(urlPathMatching(MB_LOOKUP)).willReturn(fixture("lookup-ok-computer.json")));

        client.get().uri("/albums/{id}", OK_COMPUTER).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("OK Computer")
                .jsonPath("$.artistCredit").isEqualTo("Radiohead")
                .jsonPath("$.primaryArtistId").isEqualTo(RADIOHEAD);
        // Stored: the second request doesn't go to MusicBrainz.
        client.get().uri("/albums/{id}", OK_COMPUTER).exchange().expectStatus().isOk();

        musicBrainz.verify(1, getRequestedFor(urlPathMatching(MB_LOOKUP)));
        assertThat(albums.findById(UUID.fromString(OK_COMPUTER))).isPresent();
    }

    @Test
    void albumMusicBrainzDoesNotKnowIs404() {
        musicBrainz.stubFor(get(urlPathMatching(MB_LOOKUP)).willReturn(notFound()));

        client.get().uri("/albums/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:album-not-found");
        assertThat(count("albums")).isZero();
    }

    @Test
    void releaseGroupThatIsNotAnAlbumIs404() {
        musicBrainz.stubFor(get(urlPathMatching(MB_LOOKUP)).willReturn(fixture("lookup-creep-single.json")));

        client.get().uri("/albums/{id}", CREEP_SINGLE).exchange().expectStatus().isNotFound();
        assertThat(count("albums")).isZero();
    }

    @Test
    void musicBrainzDownOnAMissIs503() {
        musicBrainz.stubFor(get(urlPathMatching(MB_LOOKUP)).willReturn(serverError()));

        client.get().uri("/albums/{id}", OK_COMPUTER).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.type").isEqualTo("urn:crate:problem:catalog-source-unavailable");
    }

    @Test
    void malformedIdIsRejected() {
        client.get().uri("/albums/not-a-uuid").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    // --- GET /albums?ids= ---

    @Test
    void batchReturnsStoredAlbumsInRequestOrder() {
        ReleaseGroup first = seed("Homogenic", "Björk");
        ReleaseGroup second = seed("OK Computer", "Radiohead");
        UUID unknown = UUID.randomUUID();

        client.get().uri("/albums?ids={ids}", second.id() + "," + unknown + "," + first.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].id").isEqualTo(second.id().toString())
                .jsonPath("$.items[1].id").isEqualTo(first.id().toString())
                .jsonPath("$.items[1].coverUrl").isEqualTo(
                        "https://coverartarchive.org/release-group/" + first.id() + "/front-250");

        // Batch is local only: feeds reference albums that were stored when someone rated them.
        musicBrainz.verify(0, getRequestedFor(urlPathMatching(MB_LOOKUP)));
    }

    @Test
    void batchAcceptsOneHundredIds() {
        String ids = Stream.generate(UUID::randomUUID).limit(100).map(UUID::toString).collect(Collectors.joining(","));

        client.get().uri("/albums?ids={ids}", ids).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    void batchRejectsMoreThanOneHundredIds() {
        String ids = Stream.generate(UUID::randomUUID).limit(101).map(UUID::toString).collect(Collectors.joining(","));

        client.get().uri("/albums?ids={ids}", ids).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
    }

    @Test
    void batchRejectsEmptyMissingOrMalformedIds() {
        client.get().uri("/albums?ids=").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
        client.get().uri("/albums").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
        client.get().uri("/albums?ids=nope").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ids");
    }
}
