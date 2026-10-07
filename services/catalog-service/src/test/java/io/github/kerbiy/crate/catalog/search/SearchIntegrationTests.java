package io.github.kerbiy.crate.catalog.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzQueryBuilder;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;

class SearchIntegrationTests extends CatalogIntegrationTest {

    private static final String OK_COMPUTER = "b1392450-e666-3926-a536-22c65f834433";
    private static final UUID RADIOHEAD = UUID.fromString("a74b1b7f-71a5-4011-9441-d0b5e4122711");

    // --- ranking (ADR 010) ---

    @Test
    void artistQueryPutsTheArtistsMostReleasedAlbumsFirst() {
        stubArtistQuery();

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(false)
                .jsonPath("$.items[0].title").isEqualTo("OK Computer")
                .jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER)
                .jsonPath("$.items[1].title").isEqualTo("Pablo Honey")
                .jsonPath("$.items[2].title").isEqualTo("The Bends")
                // Then what the query itself found, starting with the self-titled EP.
                .jsonPath("$.items[10].title").isEqualTo("Radiohead")
                .jsonPath("$.items[*].title").value(List.class, titles -> assertThat(titles)
                        .doesNotContain("Radiohead Box"))  // a compilation
                // Several albums are called "Radiohead"; each one is listed once.
                .jsonPath("$.items[*].id").value(List.class, ids -> assertThat(ids).doesNotHaveDuplicates());

        // The query, then the artist's albums. No fuzzy retry: the first query found something.
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH))
                .withQueryParam("query", equalTo(MusicBrainzQueryBuilder.artistAlbums(RADIOHEAD))));
    }

    @Test
    void resultsCarryCoverYearAndRatingFields() {
        stubArtistQuery();

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].artistCredit").isEqualTo("Radiohead")
                .jsonPath("$.items[0].coverUrl").isEqualTo(
                        "https://coverartarchive.org/release-group/" + OK_COMPUTER + "/front-250")
                .jsonPath("$.items[0].year").isEqualTo("1997")
                .jsonPath("$.items[0].ratingCount").isEqualTo(0)
                .jsonPath("$.items[0].avgRating").doesNotExist();
    }

    @Test
    void liveAlbumIsFoundWhenItsTitleIsExactlyTheQuery() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(fixture("search-at-folsom-prison.json")));

        // Johnny Cash's (Live, 20 releases, score 66) outranks a soundtrack of the same name (1 release, score 100).
        search("at folsom prison")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].artistCredit").isEqualTo("Johnny Cash")
                .jsonPath("$.items[1].artistCredit").isEqualTo("Los Tigres del Norte");
        assertThat(jdbc.sql("select secondary_types[1] from albums where artist_credit = 'Johnny Cash'")
                .query(String.class).single()).isEqualTo("Live");
    }

    @Test
    void misspellingIsRetriedFuzzilyOnlyWhenNothingWasFound() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", notContaining("~"))
                .willReturn(emptySearch()));
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", containing("~"))
                .willReturn(fixture("search-radiohead.json")));

        search("radiohed")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].title").isEqualTo("Radiohead");

        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH))
                .withQueryParam("query", equalTo(MusicBrainzQueryBuilder.build("radiohed", true).lucene())));
    }

    // --- read-through behaviour ---

    @Test
    void storedAlbumsDoNotStopMusicBrainzFromBeingAsked() {
        // Five local matches used to be "good enough"; that's how Kid A went missing for "radiohead kid a".
        seedRadioheadAlbums();
        stubArtistQuery();

        search("radiohead").expectStatus().isOk().expectBody().jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER);

        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void cachedQueryReplaysMusicBrainzsRankingWithoutAskingAgain() {
        stubArtistQuery();
        List<?> first = titles("radiohead");

        // Different spelling, same normalized query.
        List<?> again = titles("  RADIOHEAD ");

        assertThat(again).isEqualTo(first);
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(count("search_results")).isEqualTo(first.size());
        assertThat(jdbc.sql("select normalized_query from search_cache").query(String.class).list())
                .containsExactly("radiohead");
    }

    @Test
    void cachedQueryOlderThanTtlIsAskedAgainAndItsRankingReplaced() {
        stubArtistQuery();
        search("radiohead").expectStatus().isOk();
        jdbc.sql("update search_cache set fetched_at = now() - interval '8 days'").update();
        musicBrainz.resetAll();
        stubCoversExist();
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(emptySearch()));

        search("radiohead").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0);

        assertThat(count("search_results")).isZero();
        assertThat(count("albums")).isPositive(); // albums stay; only the ranking was replaced
    }

    @Test
    void emptyMusicBrainzAnswerIsCachedToo() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(emptySearch()));

        search("qwxzv").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0);
        search("qwxzv").expectStatus().isOk();

        // Exact and fuzzy once, then never again within the TTL.
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(count("search_cache")).isEqualTo(1);
    }

    @Test
    void limitCapsResults() {
        stubArtistQuery();

        search("radiohead", "2").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(2);
    }

    // --- covers (Cover Art Archive) ---

    @Test
    void albumWithoutACoverIsLeftOutOfSearchButStillHasItsPage() {
        stubArtistQuery();
        stubNoCover(UUID.fromString(OK_COMPUTER));

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].title").isEqualTo("Pablo Honey")
                .jsonPath("$.items[*].id").value(List.class, ids -> assertThat(ids).doesNotContain(OK_COMPUTER));

        client.get().uri("/albums/" + OK_COMPUTER).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.title").isEqualTo("OK Computer");
        assertThat(jdbc.sql("select has_cover from albums where id = cast(:id as uuid)").param("id", OK_COMPUTER)
                .query(Boolean.class).single()).isFalse();
    }

    @Test
    void coldSearchAsksTheArchiveOnceForEachAlbumAndWarmSearchNever() {
        stubArtistQuery();
        search("radiohead").expectStatus().isOk();
        int ranked = count("search_results");
        musicBrainz.verify(ranked, anyRequestedFor(urlPathMatching(CAA + "/.*")));
        musicBrainz.resetRequests();

        search("radiohead").expectStatus().isOk();

        musicBrainz.verify(0, anyRequestedFor(urlPathMatching(CAA + "/.*")));
    }

    @Test
    void knownCoverIsNeverRecheckedButOldNoCoverIs() {
        stubArtistQuery();
        search("radiohead").expectStatus().isOk();
        // OK Computer: "no cover" a month ago. Kid A: "no cover" yesterday.
        jdbc.sql("""
                update albums set has_cover = false, cover_checked_at = now() - interval '31 days'
                where title = 'OK Computer'
                """).update();
        jdbc.sql("""
                update albums set has_cover = false, cover_checked_at = now() - interval '1 day'
                where title = 'Kid A'
                """).update();
        jdbc.sql("update search_cache set fetched_at = now() - interval '8 days'").update();
        musicBrainz.resetRequests();

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                // Rechecked, and it has a cover now; Kid A stays hidden until its month is up.
                .jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER)
                .jsonPath("$.items[*].title").value(List.class, titles -> assertThat(titles).doesNotContain("Kid A"));

        musicBrainz.verify(1, anyRequestedFor(urlPathMatching(CAA + "/.*")));
        musicBrainz.verify(1, anyRequestedFor(urlPathEqualTo(CAA + "/" + OK_COMPUTER + "/front-250")));
    }

    @Test
    void archiveDownMeansAlbumsAreShownAndAskedAgainNextTime() {
        stubArtistQuery();
        musicBrainz.stubFor(any(urlPathMatching(CAA + "/.*")).atPriority(1).willReturn(serviceUnavailable()));

        search("radiohead").expectStatus().isOk().expectBody()
                .jsonPath("$.partial").isEqualTo(false)
                .jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER);

        assertThat(jdbc.sql("select count(*) from albums where has_cover is not null").query(Integer.class).single())
                .isZero();
    }

    @Test
    void fallbackLeavesOutAlbumsWithoutACover() {
        ReleaseGroup withCover = seed("OK Computer", "Radiohead");
        ReleaseGroup without = seed("Kid A", "Radiohead");
        albums.recordCovers(Map.of(withCover.id(), true, without.id(), false));
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("radiohead").expectStatus().isOk().expectBody()
                .jsonPath("$.items[*].title").isEqualTo(List.of("OK Computer"));
    }

    // --- MusicBrainz unavailable: local fallback ---

    @Test
    void musicBrainzErrorReturnsLocalResultsAsPartial() {
        seed("OK Computer", "Radiohead");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(true)
                .jsonPath("$.items[0].title").isEqualTo("OK Computer");

        // A failed call isn't a cached answer: the next search tries again.
        assertThat(count("search_cache")).isZero();
    }

    @Test
    void fallbackLeavesOutExcludedTypesAndPrefersPopularAlbums() {
        seed("Creep", "Radiohead", 4, List.of());
        seed("Radiohead Box", "Radiohead", 1, List.of("Compilation"));
        seed("OK Computer", "Radiohead", 39, List.of());
        seed("Radiohead", "Radiohead", 1, List.of("Live")); // exact title: kept
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[*].title").isEqualTo(List.of("OK Computer", "Creep", "Radiohead"));
    }

    @Test
    void failureOfTheArtistRequestIsAFailureToo() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", notContaining("arid:"))
                .willReturn(fixture("search-radiohead.json")));
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", containing("arid:"))
                .willReturn(serverError()));

        search("radiohead").expectStatus().isOk().expectBody().jsonPath("$.partial").isEqualTo(true);

        assertThat(count("search_cache")).isZero();
    }

    @Test
    void musicBrainzUnreachableReturnsPartial() {
        seed("OK Computer", "Radiohead");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(true)
                .jsonPath("$.items.length()").isEqualTo(1);
    }

    @Test
    void musicBrainzStill503AfterRetriesReturnsPartial() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serviceUnavailable()));

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(true)
                .jsonPath("$.items.length()").isEqualTo(0);

        musicBrainz.verify(3, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void fallbackFindsAlbumsWithoutDiacriticsInTheQuery() {
        seed("Homogenic", "Björk");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("bjork")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].title").isEqualTo("Homogenic")
                .jsonPath("$.items[0].artistCredit").isEqualTo("Björk");
    }

    @Test
    void fallbackToleratesASmallTypo() {
        seedRadioheadAlbums();
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("radiohed").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(5);
    }

    @Test
    void fallbackLeavesOutUnrelatedAlbums() {
        seedRadioheadAlbums();
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(serverError()));

        search("bjork").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0);
    }

    // --- concurrency ---

    @Test
    void concurrentSearchesForSameNewTermDoNotCreateDuplicates() throws Exception {
        // The delay makes both requests miss locally and be inside MusicBrainz at the same time.
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH))
                .willReturn(fixture("search-radiohead-ok-computer.json").withFixedDelay(300)));

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> searchOnce = () -> {
            start.await();
            return search("Radiohead - OK Computer").returnResult(String.class).getStatus().value();
        };
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(searchOnce));
            results.add(pool.submit(searchOnce));
            start.countDown();
        }

        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(200);
        }
        // Both really missed and both wrote the same rows: proof the race happened.
        // The fixture's other four release groups are live albums and compilations, left out.
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(count("albums")).isEqualTo(1);
        assertThat(count("artists")).isEqualTo(1);
        assertThat(count("search_cache")).isEqualTo(1);
        assertThat(count("search_results")).isEqualTo(1);
    }

    // --- validation ---

    @Test
    void missingQueryIsRejected() {
        client.get().uri("/albums/search").exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                .jsonPath("$.errors[0].field").isEqualTo("q");
    }

    @Test
    void blankOrTooLongQueryIsRejected() {
        expectInvalid(search(" "), "q");
        expectInvalid(search("a".repeat(101)), "q");
        // Only an accent: nothing left after normalizing.
        expectInvalid(search("́"), "q");
    }

    @Test
    void limitOutOfRangeIsRejected() {
        expectInvalid(search("radiohead", "0"), "limit");
        expectInvalid(search("radiohead", "51"), "limit");
        expectInvalid(search("radiohead", "abc"), "limit");
    }

    private void expectInvalid(RestTestClient.ResponseSpec response, String field) {
        Map<String, Object> body = response.expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();
        assertThat(body).containsEntry("type", "urn:crate:problem:validation-failed");
        assertThat(body.get("errors")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .extracting("field").contains(field);
    }

    private RestTestClient.ResponseSpec search(String q) {
        return client.get().uri(uri -> uri.path("/albums/search").queryParam("q", "{q}").build(q)).exchange();
    }

    private RestTestClient.ResponseSpec search(String q, String limit) {
        return client.get()
                .uri(uri -> uri.path("/albums/search").queryParam("q", "{q}").queryParam("limit", "{limit}").build(q, limit))
                .exchange();
    }

    private List<?> titles(String q) {
        return search(q).expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody().get("items") instanceof List<?> items
                ? items.stream().map(item -> ((Map<?, ?>) item).get("title")).toList()
                : List.of();
    }

    /** "radiohead": the query's own answer, and Radiohead's albums for the artist request. */
    private void stubArtistQuery() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", notContaining("arid:"))
                .willReturn(fixture("search-radiohead.json")));
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", containing("arid:"))
                .willReturn(fixture("artist-albums-radiohead.json")));
    }

    private void seedRadioheadAlbums() {
        for (String title : List.of("OK Computer", "Kid A", "In Rainbows", "Amnesiac", "The Bends")) {
            seed(title, "Radiohead");
        }
    }
}
