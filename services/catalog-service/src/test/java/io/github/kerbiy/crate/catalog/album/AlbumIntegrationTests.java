package io.github.kerbiy.crate.catalog.album;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.time.Duration;
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

    // --- ListenBrainz numbers on the album page: refreshed in the background (ADR 012) ---

    @Test
    void missingNumbersAreFetchedInTheBackground() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubPopularity(album.id(), 25280, 1781830, 300);

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz").doesNotExist()
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(true);

        await().atMost(Duration.ofSeconds(5)).until(() -> listens(album.id()) != null);
        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz.listeners").isEqualTo(25280)
                .jsonPath("$.listenBrainz.listens").isEqualTo(1781830)
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(false);
        assertThat(listenBrainzCalls(album.id())).isEqualTo(1);
    }

    @Test
    void albumNeverWaitsForListenBrainz() {
        // review-service checks that an album exists with this request before saving a rating.
        // ListenBrainz takes 2 s here; the album answers before even the client's 400 ms timeout.
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubPopularity(album.id(), 25280, 1781830, 2000);
        client.get().uri("/albums/{id}", seed("Warm-up", "Nobody").id()).exchange().expectStatus().isOk();

        long start = System.nanoTime();
        client.get().uri("/albums/{id}", album.id()).exchange().expectStatus().isOk();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(400));
    }

    @Test
    void freshNumbersAreNotAskedAgain() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        storeNumbers(album.id(), 25280, 1781830, "1 day");

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz.listeners").isEqualTo(25280)
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(false);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).until(() -> listenBrainzCalls(album.id()) == 0);
    }

    @Test
    void numbersOlderThanThirtyDaysAreShownThenRefreshed() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        storeNumbers(album.id(), 20000, 1500000, "31 days");
        stubPopularity(album.id(), 25280, 1781830, 0);

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz.listeners").isEqualTo(20000)
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(true);

        await().atMost(Duration.ofSeconds(5)).until(() -> Long.valueOf(1781830).equals(listens(album.id())));
    }

    @Test
    void listenBrainzFailingStoresNothingAndIsAskedAgainNextTime() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        musicBrainz.stubFor(post(urlPathEqualTo(LB_POPULARITY)).atPriority(1).willReturn(serverError()));

        client.get().uri("/albums/{id}", album.id()).exchange().expectStatus().isOk();
        await().atMost(Duration.ofSeconds(5)).until(() -> listenBrainzCalls(album.id()) == 1);

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz").doesNotExist()
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(true);
        await().atMost(Duration.ofSeconds(5)).until(() -> listenBrainzCalls(album.id()) == 2);
        assertThat(listens(album.id())).isNull();
    }

    @Test
    void viewsWhileARefreshIsRunningJoinIt() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        stubPopularity(album.id(), 25280, 1781830, 300);

        client.get().uri("/albums/{id}", album.id()).exchange().expectStatus().isOk();
        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.listenBrainzRefreshing").isEqualTo(true);

        await().atMost(Duration.ofSeconds(5)).until(() -> listens(album.id()) != null);
        assertThat(listenBrainzCalls(album.id())).isEqualTo(1);
    }

    @Test
    void anAlbumListenBrainzDoesNotKnowShowsNoNumbers() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");
        storeNumbers(album.id(), 0, 0, "1 day");

        client.get().uri("/albums/{id}", album.id()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.listenBrainz").doesNotExist()
                .jsonPath("$.listenBrainzRefreshing").isEqualTo(false);
    }

    @Test
    void batchNeverAsksListenBrainz() {
        ReleaseGroup album = seed("OK Computer", "Radiohead");

        client.get().uri("/albums?ids={ids}", album.id()).exchange().expectStatus().isOk();

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).until(() -> listenBrainzCalls(album.id()) == 0);
    }

    /** ListenBrainz's answer for this album, after {@code delayMs}. */
    private void stubPopularity(UUID id, int listeners, long listens, int delayMs) {
        ResponseDefinitionBuilder answer = aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""
                        [{"release_group_mbid": "%s", "total_listen_count": %d, "total_user_count": %d}]
                        """.formatted(id, listens, listeners))
                .withFixedDelay(delayMs);
        musicBrainz.stubFor(post(urlPathEqualTo(LB_POPULARITY)).atPriority(1)
                .withRequestBody(matchingJsonPath("$.release_group_mbids[?(@ == '" + id + "')]"))
                .willReturn(answer));
    }

    private void storeNumbers(UUID id, int listeners, long listens, String age) {
        jdbc.sql("""
                        update albums set listener_count = :listeners, listen_count = :listens,
                                          listeners_checked_at = now() - cast(:age as interval)
                        where id = :id
                        """)
                .param("listeners", listeners).param("listens", listens).param("age", age).param("id", id)
                .update();
    }

    /** Calls about this album only: a previous test's background call may still land after the reset. */
    private int listenBrainzCalls(UUID id) {
        RequestPatternBuilder calls = postRequestedFor(urlPathEqualTo(LB_POPULARITY))
                .withRequestBody(matchingJsonPath("$.release_group_mbids[?(@ == '" + id + "')]"));
        return musicBrainz.countRequestsMatching(calls.build()).getCount();
    }

    private Long listens(UUID id) {
        return jdbc.sql("select listen_count from albums where id = :id").param("id", id)
                .query((rs, rowNum) -> rs.getObject("listen_count", Long.class)).list().getFirst();
    }
}
