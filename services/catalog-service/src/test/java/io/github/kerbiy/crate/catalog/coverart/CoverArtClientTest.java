package io.github.kerbiy.crate.catalog.coverart;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.kerbiy.crate.catalog.coverart.CoverSource.Cover;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The client against a WireMock "Cover Art Archive", answering the way the real one does. */
class CoverArtClientTest {

    private static final String USER_AGENT = "Crate/0.1 ( test@example.com )";
    private static final UUID OK_COMPUTER = UUID.fromString("b1392450-e666-3926-a536-22c65f834433");
    private static final String FRONT = "/caa/release-group/" + OK_COMPUTER + "/front-250";

    @RegisterExtension
    static WireMockExtension archive = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private CoverArtClient client;

    @BeforeEach
    void createClient() {
        CoverArtProperties properties = new CoverArtProperties(URI.create(archive.baseUrl() + "/caa/"), 8,
                Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofDays(30));
        client = CoverArtClient.create(properties, USER_AGENT);
    }

    @Test
    void redirectToTheImageMeansThereIsACover() {
        // What CAA really answers; following it would download the image from archive.org.
        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withStatus(307)
                .withHeader("Location", archive.baseUrl() + "/archive.org/image.jpg")));

        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.PRESENT);

        archive.verify(1, newRequestPattern(RequestMethod.HEAD, urlPathEqualTo(FRONT))
                .withHeader("User-Agent", equalTo(USER_AGENT)));
        archive.verify(0, newRequestPattern(RequestMethod.ANY, urlPathEqualTo("/archive.org/image.jpg")));
    }

    @Test
    void notFoundMeansNoCover() {
        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withStatus(404)));

        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.ABSENT);
    }

    @Test
    void serverErrorOrRateLimitIsUnknown() {
        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withStatus(503)));
        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.UNKNOWN);

        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withStatus(429)));
        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.UNKNOWN);
    }

    @Test
    void slowOrBrokenConnectionIsUnknown() {
        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withStatus(404).withFixedDelay(1000)));
        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.UNKNOWN);

        archive.stubFor(any(urlPathEqualTo(FRONT)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(client.check(OK_COMPUTER)).isEqualTo(Cover.UNKNOWN);
    }
}
