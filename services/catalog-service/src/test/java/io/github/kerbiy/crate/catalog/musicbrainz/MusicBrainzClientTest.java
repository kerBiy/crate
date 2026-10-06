package io.github.kerbiy.crate.catalog.musicbrainz;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The client against a WireMock "MusicBrainz" serving responses recorded from the real API
 * (src/test/resources/__files/musicbrainz, see record-musicbrainz-fixtures.sh). The rate limiter,
 * sleeper and jitter are fakes, so retries and backoff are checked without waiting.
 */
class MusicBrainzClientTest {

    private static final String USER_AGENT = "Crate/0.1 ( test@example.com )";
    private static final String SEARCH = "/ws/2/release-group";
    private static final UUID OK_COMPUTER = UUID.fromString("b1392450-e666-3926-a536-22c65f834433");
    private static final UUID CREEP_SINGLE = UUID.fromString("c5bc370b-95c2-3634-bb89-51bb2dce97c3");
    private static final UUID RADIOHEAD = UUID.fromString("a74b1b7f-71a5-4011-9441-d0b5e4122711");
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    @RegisterExtension
    static WireMockExtension musicBrainz = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final AtomicInteger permits = new AtomicInteger();
    private final List<Duration> backoffs = new ArrayList<>();
    private MusicBrainzClient client;

    @BeforeEach
    void createClient() {
        MusicBrainzProperties properties = new MusicBrainzProperties(
                URI.create(musicBrainz.baseUrl() + "/ws/2"),
                USER_AGENT,
                Duration.ofSeconds(1),
                READ_TIMEOUT,
                new MusicBrainzProperties.RateLimit(Duration.ofSeconds(1), Duration.ofSeconds(10)),
                new MusicBrainzProperties.Retry(3, Duration.ofSeconds(1)));
        // Jitter fixed at 0.5: halfway through each backoff window.
        client = MusicBrainzClient.create(properties, permits::incrementAndGet, backoffs::add, () -> 0.5);
    }

    private static ResponseDefinitionBuilder fixture(String name) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBodyFile("musicbrainz/" + name);
    }

    // --- search ---

    @Test
    void searchMapsMusicBrainzJsonToReleaseGroups() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));

        List<ReleaseGroup> results = client.searchReleaseGroups("Radiohead - OK Computer", 5);

        assertThat(results).hasSize(5);
        assertThat(results.getFirst()).isEqualTo(new ReleaseGroup(OK_COMPUTER, "OK Computer", "Radiohead",
                "Album", "1997-05-21", List.of(new ArtistCredit(RADIOHEAD, "Radiohead", "Radiohead"))));
        // "Idiot Computer" has no first-release-date in MusicBrainz.
        assertThat(results).filteredOn(r -> r.title().equals("Idiot Computer"))
                .singleElement().extracting(ReleaseGroup::firstReleaseDate).isNull();
    }

    @Test
    void searchKeepsOnlyAlbumsAndEps() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-all-types.json")));

        List<ReleaseGroup> results = client.searchReleaseGroups("Radiohead", 10);

        // The fixture has 6 release groups; the 3 Singles are dropped.
        assertThat(results).extracting(ReleaseGroup::title, ReleaseGroup::primaryType).containsExactly(
                tuple("OK Computer", "Album"),
                tuple("Creep", "EP"),
                tuple("Airbag / How Am I Driving?", "EP"));
    }

    @Test
    void artistCreditJoinsCreditedNamesWithJoinPhrases() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-jay-z-watch-the-throne.json")));

        ReleaseGroup throne = client.searchReleaseGroups("JAY-Z - Watch the Throne", 3).getFirst();

        // Credited as "Kanye West" on this album, though the artist is called "Ye" today:
        // the credit string keeps the names as printed on the album, the artist list the real artists.
        assertThat(throne.artistCredit()).isEqualTo("Jay‐Z & Kanye West");
        assertThat(throne.artists()).extracting(ArtistCredit::id, ArtistCredit::name).containsExactly(
                tuple(UUID.fromString("f82bcf78-5b69-4622-a5ef-73800768d9ac"), "JAŸ-Z"),
                tuple(UUID.fromString("164f0d73-1234-4e2c-8743-d77bf2191051"), "Ye"));
    }

    @Test
    void searchSendsUserAgentJsonFormatAndTheBuiltQuery() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));

        client.searchReleaseGroups("AC/DC - Back in Black", 7);

        // WireMock decodes the query string, so this also proves \, /, ( and : survived URL encoding.
        musicBrainz.verify(1, getRequestedFor(urlPathEqualTo(SEARCH))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParam("fmt", equalTo("json"))
                .withQueryParam("limit", equalTo("7"))
                .withQueryParam("query", equalTo(MusicBrainzQueryBuilder.build("AC/DC - Back in Black"))));
    }

    @Test
    void queryWithAmpersandIsOneParameter() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));

        client.searchReleaseGroups("Simon & Garfunkel - Bookends", 5);

        // An unencoded & would end the parameter at "Simon ".
        musicBrainz.verify(getRequestedFor(urlPathEqualTo(SEARCH))
                .withQueryParam("query", equalTo(MusicBrainzQueryBuilder.build("Simon & Garfunkel - Bookends"))));
    }

    // --- lookup ---

    @Test
    void lookupMapsOneReleaseGroup() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH + "/" + OK_COMPUTER))
                .withQueryParam("inc", equalTo("artist-credits"))
                .withQueryParam("fmt", equalTo("json"))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .willReturn(fixture("lookup-ok-computer.json")));

        assertThat(client.lookupReleaseGroup(OK_COMPUTER)).contains(new ReleaseGroup(OK_COMPUTER, "OK Computer",
                "Radiohead", "Album", "1997-05-21", List.of(new ArtistCredit(RADIOHEAD, "Radiohead", "Radiohead"))));
    }

    @Test
    void lookupOfASingleIsEmpty() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH + "/" + CREEP_SINGLE)).willReturn(fixture("lookup-creep-single.json")));

        assertThat(client.lookupReleaseGroup(CREEP_SINGLE)).isEmpty();
    }

    @Test
    void lookupOfAnUnknownMbidIsEmpty() {
        musicBrainz.stubFor(get(urlPathMatching(SEARCH + "/.*")).willReturn(notFound()));

        assertThat(client.lookupReleaseGroup(UUID.randomUUID())).isEmpty();
        assertThat(backoffs).isEmpty(); // 404 is an answer, not something to retry
    }

    // --- 503, retries, rate limiting ---

    @Test
    void retriesAfter503WithJitteredBackoff() {
        stubSequence(serviceUnavailable(), serviceUnavailable(), fixture("lookup-ok-computer.json"));

        assertThat(client.lookupReleaseGroup(OK_COMPUTER)).isPresent();

        musicBrainz.verify(3, getRequestedFor(urlPathEqualTo(SEARCH + "/" + OK_COMPUTER)));
        // Windows: 0.5–1 s, then 1–2 s; jitter 0.5 lands in the middle of each.
        assertThat(backoffs).containsExactly(Duration.ofMillis(750), Duration.ofMillis(1500));
    }

    @Test
    void givesUpAfterThreeAttempts() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH + "/" + OK_COMPUTER)).willReturn(serviceUnavailable()));

        assertThatThrownBy(() -> client.lookupReleaseGroup(OK_COMPUTER))
                .isInstanceOf(MusicBrainzException.class)
                .hasMessageContaining("503");

        musicBrainz.verify(3, getRequestedFor(urlPathEqualTo(SEARCH + "/" + OK_COMPUTER)));
        assertThat(backoffs).hasSize(2); // no pointless sleep after the last attempt
    }

    @Test
    void everyAttemptTakesARateLimitPermit() {
        stubSequence(serviceUnavailable(), fixture("lookup-ok-computer.json"));

        client.lookupReleaseGroup(OK_COMPUTER);
        client.searchReleaseGroups("Radiohead", 5);

        // 2 attempts for the lookup + 1 for the search: retries count against MusicBrainz's limit too.
        assertThat(permits).hasValue(3);
        assertThat(musicBrainz.getAllServeEvents()).hasSize(3);
    }

    @Test
    void jitterSpansTheWholeWindow() {
        MusicBrainzProperties.Retry retry = new MusicBrainzProperties.Retry(3, Duration.ofSeconds(1));
        MusicBrainzClient noJitter = new MusicBrainzClient(null, () -> { }, retry, d -> { }, () -> 0.0);
        MusicBrainzClient maxJitter = new MusicBrainzClient(null, () -> { }, retry, d -> { }, () -> 0.999_999);

        assertThat(noJitter.backoff(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(maxJitter.backoff(1)).isLessThan(Duration.ofSeconds(1)).isGreaterThan(Duration.ofMillis(999));
        assertThat(noJitter.backoff(2)).isEqualTo(Duration.ofSeconds(1));
        assertThat(maxJitter.backoff(2)).isLessThan(Duration.ofSeconds(2)).isGreaterThan(Duration.ofMillis(1999));
    }

    @Test
    void rateLimiterRefusalMeansNoRequest() {
        MusicBrainzClient refused = MusicBrainzClient.create(
                new MusicBrainzProperties(URI.create(musicBrainz.baseUrl() + "/ws/2"), USER_AGENT,
                        Duration.ofSeconds(1), READ_TIMEOUT,
                        new MusicBrainzProperties.RateLimit(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                        new MusicBrainzProperties.Retry(3, Duration.ofSeconds(1))),
                () -> {
                    throw new MusicBrainzException("MusicBrainz rate limit queue is full");
                },
                d -> { }, () -> 0.5);

        assertThatThrownBy(() -> refused.searchReleaseGroups("Radiohead", 5)).isInstanceOf(MusicBrainzException.class);
        assertThat(musicBrainz.getAllServeEvents()).isEmpty();
    }

    // --- other failures ---

    @Test
    void slowResponseTimesOut() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")
                .withFixedDelay((int) READ_TIMEOUT.multipliedBy(3).toMillis())));

        assertThatThrownBy(() -> client.searchReleaseGroups("Radiohead", 5))
                .isInstanceOf(MusicBrainzException.class);
        assertThat(backoffs).isEmpty(); // timeouts aren't retried: the user is waiting
    }

    @Test
    void unexpectedStatusIsAnError() {
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.searchReleaseGroups("Radiohead", 5))
                .isInstanceOf(MusicBrainzException.class)
                .hasMessageContaining("400");
    }

    /** Lookup of OK Computer answers with each response in turn. */
    private static void stubSequence(ResponseDefinitionBuilder... responses) {
        for (int i = 0; i < responses.length; i++) {
            musicBrainz.stubFor(get(urlPathEqualTo(SEARCH + "/" + OK_COMPUTER))
                    .inScenario("sequence")
                    .whenScenarioStateIs(i == 0 ? STARTED : "step " + i)
                    .willSetStateTo("step " + (i + 1))
                    .willReturn(responses[i]));
        }
        musicBrainz.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(fixture("search-radiohead-ok-computer.json")));
    }
}
