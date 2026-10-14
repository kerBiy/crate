package io.github.kerbiy.crate.catalog.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Listener counts from ListenBrainz in the ranking (ADR 012), and search without them when it fails. */
class SearchListenersIntegrationTests extends CatalogIntegrationTest {

    // From search-at-folsom-prison.json. Without listeners Johnny Cash's live album is first:
    // 66 + 15·ln 21 ≈ 111.7 against the soundtrack's 100 + 15·ln 2 ≈ 110.4.
    private static final String JOHNNY_CASH = "6b4ea595-3378-3019-be5f-058412670791";
    private static final String LOS_TIGRES = "5a64feb1-09df-49cb-8afd-79230db6bf10";

    @BeforeEach
    void stubMusicBrainz() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).willReturn(fixture("search-at-folsom-prison.json")));
    }

    @Test
    void listenersLiftTheAlbumPeoplePlay() {
        // + 2·ln(1 + 1000) ≈ 13.8: the soundtrack (≈ 124.2) passes Johnny Cash.
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0));

        expectOrder(LOS_TIGRES, JOHNNY_CASH);

        // One call for the whole search, with the query's results and our User-Agent.
        musicBrainz.verify(1, postRequestedFor(urlPathEqualTo(LB_POPULARITY))
                .withHeader("User-Agent", equalTo("Crate/0.1 ( test@example.com )"))
                .withRequestBody(matchingJsonPath("$.release_group_mbids[?(@ == '" + LOS_TIGRES + "')]"))
                .withRequestBody(matchingJsonPath("$.release_group_mbids[?(@ == '" + JOHNNY_CASH + "')]")));
        // ListenBrainz changes nothing about what MusicBrainz is asked.
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(listeners(LOS_TIGRES)).isEqualTo(1000);
        assertThat(listeners(JOHNNY_CASH)).isZero();
        // Total listens are stored next to them, for the album page.
        assertThat(listens(LOS_TIGRES)).isEqualTo(10000);
    }

    @Test
    void storedCountsAreUsedForThirtyDaysThenAskedAgain() {
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0));
        expectOrder(LOS_TIGRES, JOHNNY_CASH);

        // The query's cache entry expires; its albums' counts are still fresh: no ListenBrainz call,
        // and still the same order.
        jdbc.sql("delete from search_cache").update();
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        musicBrainz.verify(1, postRequestedFor(urlPathEqualTo(LB_POPULARITY)));

        // A month later they are asked again.
        jdbc.sql("update albums set listeners_checked_at = now() - interval '31 days'").update();
        jdbc.sql("delete from search_cache").update();
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        musicBrainz.verify(2, postRequestedFor(urlPathEqualTo(LB_POPULARITY)));
    }

    @Test
    void slowListenBrainzMeansTheRankingWithoutListeners() {
        // Longer than the 400 ms timeout.
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0).withFixedDelay(2000));

        long start = System.nanoTime();
        expectOrder(JOHNNY_CASH, LOS_TIGRES);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1800));
        assertThat(count("albums where listeners_checked_at is not null")).isZero();
    }

    @Test
    void listenBrainzDownMeansTheRankingWithoutListeners() {
        stubListeners(serverError());

        expectOrder(JOHNNY_CASH, LOS_TIGRES);
        assertThat(count("albums where listeners_checked_at is not null")).isZero();
    }

    @Test
    void droppedConnectionMeansTheRankingWithoutListeners() {
        stubListeners(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        expectOrder(JOHNNY_CASH, LOS_TIGRES);
    }

    @Test
    void storedCountsAreIgnoredWhenListenBrainzFails() {
        // Los Tigres' count is stored, Johnny Cash's is missing, and then ListenBrainz is down: ranking
        // with only some counts would favour whatever happened to be stored, so none are used.
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0));
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        jdbc.sql("update albums set listeners_checked_at = null where id = cast(:id as uuid)")
                .param("id", JOHNNY_CASH).update();
        jdbc.sql("delete from search_cache").update();
        stubListeners(serverError());

        expectOrder(JOHNNY_CASH, LOS_TIGRES);
    }

    // --- how long the ranking is cached ---

    @Test
    void rankingWithoutListenersIsCachedForADayNotAWeek() {
        stubListeners(serverError());
        expectOrder(JOHNNY_CASH, LOS_TIGRES);
        assertThat(withoutListeners()).isTrue();

        // 23 hours later: still served from the cache.
        ageCache("23 hours");
        expectOrder(JOHNNY_CASH, LOS_TIGRES);
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH)));

        // 25 hours later, ListenBrainz is back: MusicBrainz is asked again and the ranking has listeners.
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0));
        ageCache("25 hours");
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        musicBrainz.verify(2, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
        assertThat(withoutListeners()).isFalse();
    }

    @Test
    void rankingWithListenersIsStillCachedForAWeek() {
        stubListeners(okJson(LOS_TIGRES, 1000, JOHNNY_CASH, 0));
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        assertThat(withoutListeners()).isFalse();

        ageCache("25 hours");
        expectOrder(LOS_TIGRES, JOHNNY_CASH);
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(MB_SEARCH)));
    }

    private void ageCache(String interval) {
        jdbc.sql("update search_cache set fetched_at = now() - cast(:interval as interval)")
                .param("interval", interval).update();
    }

    private boolean withoutListeners() {
        return jdbc.sql("select without_listeners from search_cache").query(Boolean.class).single();
    }

    private void expectOrder(String first, String second) {
        client.get().uri(uri -> uri.path("/albums/search").queryParam("q", "{q}").build("at folsom prison")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.partial").isEqualTo(false)
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].id").isEqualTo(first)
                .jsonPath("$.items[1].id").isEqualTo(second);
    }

    private void stubListeners(ResponseDefinitionBuilder answer) {
        musicBrainz.stubFor(post(urlPathEqualTo(LB_POPULARITY)).atPriority(1).willReturn(answer));
    }

    private static ResponseDefinitionBuilder okJson(String id1, int listeners1, String id2, int listeners2) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                [{"release_group_mbid": "%s", "total_listen_count": %d, "total_user_count": %d},
                 {"release_group_mbid": "%s", "total_listen_count": %d, "total_user_count": %d}]
                """.formatted(id1, listeners1 * 10, listeners1, id2, listeners2 * 10, listeners2));
    }

    private long listens(String id) {
        return jdbc.sql("select listen_count from albums where id = cast(:id as uuid)")
                .param("id", id).query(Long.class).single();
    }

    private int listeners(String id) {
        return jdbc.sql("select listener_count from albums where id = cast(:id as uuid)")
                .param("id", id).query(Integer.class).single();
    }
}
