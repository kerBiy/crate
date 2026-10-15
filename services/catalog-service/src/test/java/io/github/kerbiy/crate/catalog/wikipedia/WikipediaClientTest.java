package io.github.kerbiy.crate.catalog.wikipedia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.RateLimiter;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The client against a WireMock "Wikidata" (under /wd) and "Wikipedia" (under /wp), serving answers
 * recorded from the real APIs (src/test/resources/__files/wikidata and wikipedia).
 */
class WikipediaClientTest {

    private static final String USER_AGENT = "Crate/0.1 ( test@example.com ) Java-http-client/25";
    private static final String WIKIDATA = "/wd/w/api.php";
    private static final String SUMMARY = "/wp/api/rest_v1/page/summary/";
    private static final Duration TIMEOUT = Duration.ofMillis(300);

    @RegisterExtension
    static WireMockExtension wikimedia = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final AtomicInteger permits = new AtomicInteger();

    // --- Wikidata ---

    @Test
    void englishTitleComesFromTheItemsEnwikiSitelink() {
        wikimedia.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(fixture("wikidata", "sitelinks-ok-computer.json")));

        assertThat(client().englishTitle("Q202996")).contains("OK Computer");

        // Only the English sitelink is asked for, with our User-Agent, through the rate limiter.
        wikimedia.verify(getRequestedFor(urlPathEqualTo(WIKIDATA))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .withQueryParam("action", equalTo("wbgetentities"))
                .withQueryParam("ids", equalTo("Q202996"))
                .withQueryParam("props", equalTo("sitelinks"))
                .withQueryParam("sitefilter", equalTo("enwiki")));
        assertThat(permits).hasValue(1);
    }

    @Test
    void anItemWithoutAnEnglishArticleHasNoTitle() {
        wikimedia.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(fixture("wikidata", "sitelinks-mugur-de-fluier.json")));

        assertThat(client().englishTitle("Q12735998")).isEmpty();
    }

    @Test
    void aMissingItemHasNoTitle() {
        wikimedia.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(json("""
                {"entities": {"Q999999999": {"id": "Q999999999", "missing": ""}}, "success": 1}
                """)));

        assertThat(client().englishTitle("Q999999999")).isEmpty();
    }

    // --- Wikipedia ---

    @Test
    void summaryIsTheArticlesFirstParagraphWithItsAddress() {
        wikimedia.stubFor(get(urlPathEqualTo(SUMMARY + "OK_Computer")).willReturn(fixture("wikipedia", "summary-ok-computer.json")));

        WikipediaClient.Summary summary = client().summary("OK Computer").orElseThrow();

        assertThat(summary.title()).isEqualTo("OK Computer");
        assertThat(summary.extract()).startsWith("OK Computer is the third studio album by the English rock band Radiohead");
        assertThat(summary.url()).isEqualTo(URI.create("https://en.wikipedia.org/wiki/OK_Computer"));
        wikimedia.verify(getRequestedFor(urlPathEqualTo(SUMMARY + "OK_Computer")).withHeader("User-Agent", equalTo(USER_AGENT)));
    }

    @Test
    void aTitleIsOnePathSegmentWhateverItContains() {
        wikimedia.stubFor(get(urlEqualTo(SUMMARY + "Live%2FDead_%28album%29%3F")).willReturn(json("""
                {"type": "standard", "title": "Live/Dead (album)?", "extract": "A live album.",
                 "content_urls": {"desktop": {"page": "https://en.wikipedia.org/wiki/Live/Dead_(album)%3F"}}}
                """)));

        assertThat(client().summary("Live/Dead (album)?")).isPresent();
    }

    @Test
    void aDisambiguationPageIsNotAnArticleAboutTheAlbum() {
        wikimedia.stubFor(get(urlPathEqualTo(SUMMARY + "Mercury")).willReturn(fixture("wikipedia", "summary-mercury.json")));

        assertThat(client().summary("Mercury")).isEmpty();
    }

    @Test
    void aMissingArticleIsEmpty() {
        wikimedia.stubFor(get(urlPathMatching(SUMMARY + ".+")).willReturn(notFound()));

        assertThat(client().summary("Nothing here")).isEmpty();
    }

    // --- failures ---

    @Test
    void tooManyRequestsIsAnError() {
        wikimedia.stubFor(get(urlPathMatching(SUMMARY + ".+")).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "5")));

        assertThatThrownBy(() -> client().summary("OK Computer"))
                .isInstanceOf(WikipediaException.class)
                .hasMessageContaining("429");
        wikimedia.verify(1, getRequestedFor(urlPathMatching(SUMMARY + ".+"))); // no retry
    }

    @Test
    void slowAnswerTimesOut() {
        wikimedia.stubFor(get(urlPathEqualTo(WIKIDATA)).willReturn(fixture("wikidata", "sitelinks-ok-computer.json")
                .withFixedDelay((int) TIMEOUT.multipliedBy(3).toMillis())));

        assertThatThrownBy(() -> client().englishTitle("Q202996")).isInstanceOf(WikipediaException.class);
    }

    @Test
    void aFullRateLimitQueueIsAWikipediaError() {
        WikipediaClient client = client(() -> {
            throw new MusicBrainzException("queue full");
        });

        assertThatThrownBy(() -> client.summary("OK Computer")).isInstanceOf(WikipediaException.class);
        wikimedia.verify(0, getRequestedFor(urlPathMatching(".*")));
    }

    private WikipediaClient client() {
        return client(permits::incrementAndGet);
    }

    private static WikipediaClient client(RateLimiter rateLimiter) {
        WikipediaProperties properties = new WikipediaProperties(
                URI.create(wikimedia.baseUrl() + "/wd"),
                URI.create(wikimedia.baseUrl() + "/wp"),
                TIMEOUT,
                new WikipediaProperties.RateLimit(Duration.ofSeconds(1), Duration.ofSeconds(1)));
        return WikipediaClient.create(properties, USER_AGENT, rateLimiter);
    }

    private static ResponseDefinitionBuilder fixture(String dir, String name) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBodyFile(dir + "/" + name);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
