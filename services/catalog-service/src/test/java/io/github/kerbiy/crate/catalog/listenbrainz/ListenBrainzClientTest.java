package io.github.kerbiy.crate.catalog.listenbrainz;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.RateLimiter;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The client against a WireMock "ListenBrainz", answering the way the real one does. */
class ListenBrainzClientTest {

    private static final String USER_AGENT = "Crate/0.1 ( test@example.com )";
    private static final String POPULARITY = "/lb/1/popularity/release-group";
    private static final UUID OK_COMPUTER = UUID.fromString("b1392450-e666-3926-a536-22c65f834433");
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Duration TIMEOUT = Duration.ofMillis(300);

    @RegisterExtension
    static WireMockExtension listenBrainz = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final RateLimiter noLimit = () -> { };

    @Test
    void returnsListenerCountsAndZeroForAlbumsListenBrainzDoesNotKnow() {
        // The real answer's shape: one entry per MBID, counts null when nobody listened.
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(json("""
                [{"release_group_mbid": "%s", "total_listen_count": 9542817, "total_user_count": 61402},
                 {"release_group_mbid": "%s", "total_listen_count": null, "total_user_count": null}]
                """.formatted(OK_COMPUTER, UNKNOWN))));

        assertThat(client(noLimit).popularity(List.of(OK_COMPUTER, UNKNOWN, OK_COMPUTER)))
                .containsOnly(Map.entry(OK_COMPUTER, new Popularity(61402, 9542817)), Map.entry(UNKNOWN, Popularity.UNKNOWN));

        // One call, each MBID once, with our User-Agent. Not chunked: ListenBrainz answers 400 to that.
        listenBrainz.verify(1, postRequestedFor(urlPathEqualTo(POPULARITY))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .withHeader("Content-Length", matching("\\d+"))
                .withoutHeader("Transfer-Encoding")
                .withRequestBody(equalToJson("""
                        {"release_group_mbids": ["%s", "%s"]}
                        """.formatted(OK_COMPUTER, UNKNOWN))));
    }

    @Test
    void anAlbumMissingFromTheAnswerCountsAsNoListeners() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(json("[]")));

        assertThat(client(noLimit).popularity(List.of(OK_COMPUTER))).containsEntry(OK_COMPUTER, Popularity.UNKNOWN);
    }

    @Test
    void readsTheRecordedAnswer() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("listenbrainz/popularity-ok-computer.json")));

        assertThat(client(noLimit).popularity(List.of(OK_COMPUTER, UNKNOWN)))
                .containsOnly(Map.entry(OK_COMPUTER, new Popularity(25280, 1781830)), Map.entry(UNKNOWN, Popularity.UNKNOWN));
    }

    @Test
    void noAlbumsNoRequest() {
        assertThat(client(noLimit).popularity(List.of())).isEmpty();

        listenBrainz.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void slowAnswerGivesUpAtTheTimeout() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(json("[]").withFixedDelay(3000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client(noLimit).popularity(List.of(OK_COMPUTER)))
                .isInstanceOf(ListenBrainzException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void serverErrorIsAFailure() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(serverError()));

        assertThatThrownBy(() -> client(noLimit).popularity(List.of(OK_COMPUTER)))
                .isInstanceOf(ListenBrainzException.class);
        // No retry: search is waiting.
        listenBrainz.verify(1, postRequestedFor(urlPathEqualTo(POPULARITY)));
    }

    @Test
    void tooManyRequestsIsAFailure() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY)).willReturn(aResponse().withStatus(429)
                .withHeader("X-RateLimit-Reset-In", "7")));

        assertThatThrownBy(() -> client(noLimit).popularity(List.of(OK_COMPUTER)))
                .isInstanceOf(ListenBrainzException.class);
    }

    @Test
    void droppedConnectionIsAFailure() {
        listenBrainz.stubFor(post(urlPathEqualTo(POPULARITY))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client(noLimit).popularity(List.of(OK_COMPUTER)))
                .isInstanceOf(ListenBrainzException.class);
    }

    @Test
    void fullRateLimitQueueFailsWithoutARequest() {
        RateLimiter full = () -> {
            throw new MusicBrainzException("rate limit queue is full");
        };

        assertThatThrownBy(() -> client(full).popularity(List.of(OK_COMPUTER)))
                .isInstanceOf(ListenBrainzException.class);
        listenBrainz.verify(0, anyRequestedFor(anyUrl()));
    }

    private static ListenBrainzClient client(RateLimiter rateLimiter) {
        ListenBrainzProperties properties = new ListenBrainzProperties(URI.create(listenBrainz.baseUrl() + "/lb"),
                TIMEOUT, new ListenBrainzProperties.RateLimit(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                Duration.ofDays(30));
        return ListenBrainzClient.create(properties, USER_AGENT, rateLimiter);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
