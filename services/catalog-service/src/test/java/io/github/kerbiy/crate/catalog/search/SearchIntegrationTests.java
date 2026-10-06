package io.github.kerbiy.crate.catalog.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // --- read-through behaviour ---

    @Test
    void enoughLocalMatchesDoNotCallMusicBrainz() {
        seedRadioheadAlbums();

        search("radiohead")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(false)
                .jsonPath("$.items.length()").isEqualTo(5)
                .jsonPath("$.items[0].artistCredit").isEqualTo("Radiohead")
                .jsonPath("$.items[0].coverUrl").value(String.class, url -> assertThat(url)
                        .matches("https://coverartarchive.org/release-group/[0-9a-f-]{36}/front-250"))
                .jsonPath("$.items[0].year").isEqualTo("1997")
                .jsonPath("$.items[0].ratingCount").isEqualTo(0)
                .jsonPath("$.items[0].avgRating").doesNotExist();

        musicBrainz.verify(0, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void missCallsMusicBrainzOnceAndStoresResults() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));

        search("Radiohead - OK Computer")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(false)
                .jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER)
                .jsonPath("$.items[0].title").isEqualTo("OK Computer");

        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH))
                .withQueryParam("query", equalTo(
                        "artist:(Radiohead) AND releasegroup:(OK Computer) AND primarytype:(album OR ep)")));
        assertThat(count("albums")).isEqualTo(5);
        assertThat(count("artists")).isEqualTo(1);
        assertThat(jdbc.sql("select normalized_query from search_cache").query(String.class).list())
                .containsExactly("radiohead - ok computer");
    }

    @Test
    void cachedQueryWithinTtlDoesNotCallMusicBrainzAgain() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));
        search("Radiohead - OK Computer").expectStatus().isOk();

        // Different spelling, same normalized query.
        search("radiohead  -  ok COMPUTER")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo(OK_COMPUTER);

        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void cachedQueryOlderThanTtlCallsMusicBrainzAgain() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));
        search("Radiohead - OK Computer").expectStatus().isOk();
        jdbc.sql("update search_cache set fetched_at = now() - interval '8 days'").update();

        search("Radiohead - OK Computer").expectStatus().isOk();

        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void emptyMusicBrainzAnswerIsCachedToo() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(emptySearch()));

        search("qwxzv").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0);
        search("qwxzv").expectStatus().isOk();

        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    // --- MusicBrainz unavailable ---

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

    // --- fuzzy matching ---

    @Test
    void queryWithoutDiacriticsFindsAlbumWithThem() {
        seed("Homogenic", "Björk");
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(emptySearch()));

        search("bjork")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].title").isEqualTo("Homogenic")
                .jsonPath("$.items[0].artistCredit").isEqualTo("Björk");
    }

    @Test
    void smallTypoStillMatches() {
        seedRadioheadAlbums();

        // Five matches despite the typo, so they count as good matches: no MusicBrainz call.
        search("radiohed")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items.length()").isEqualTo(5);

        musicBrainz.verify(0, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    @Test
    void unrelatedAlbumsAreNotReturned() {
        seedRadioheadAlbums();
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(emptySearch()));

        search("bjork").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    void limitCapsResults() {
        seedRadioheadAlbums();

        search("radiohead", "2").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(2);

        musicBrainz.verify(0, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
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
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(count("albums")).isEqualTo(5);
        assertThat(count("artists")).isEqualTo(1);
        assertThat(count("search_cache")).isEqualTo(1);
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

    private void seedRadioheadAlbums() {
        for (String title : List.of("OK Computer", "Kid A", "In Rainbows", "Amnesiac", "The Bends")) {
            seed(title, "Radiohead");
        }
    }
}
