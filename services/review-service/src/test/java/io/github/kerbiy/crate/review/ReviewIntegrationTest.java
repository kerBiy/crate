package io.github.kerbiy.crate.review;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.kerbiy.crate.review.events.FailingCommit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Base for review integration tests: real Postgres, WireMock as catalog-service, a clean slate per test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
// Every test imports the same classes, so they all share one Spring context (and one set of containers).
@Import({TestcontainersConfiguration.class, FailingCommit.class})
public abstract class ReviewIntegrationTest {

    protected static final String CATALOG_ALBUM = "/albums/.+";

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected WireMockServer catalog;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void resetState() {
        jdbc.sql("truncate reviews").update();
        catalog.resetAll();
        stubAlbumsExist();
    }

    /** Default answer of catalog-service: every album exists. */
    protected void stubAlbumsExist() {
        catalog.stubFor(get(urlPathMatching(CATALOG_ALBUM)).atPriority(10)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"title\": \"OK Computer\"}")));
    }

    /** PUT my review; body may be null (left out of the JSON). */
    protected RestTestClient.ResponseSpec put(UUID userId, UUID albumId, Integer rating, String body) {
        Map<String, Object> json = new HashMap<>();
        json.put("rating", rating);
        if (body != null) {
            json.put("body", body);
        }
        return client.put().uri("/reviews/albums/{albumId}", albumId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    protected int countReviews() {
        return jdbc.sql("select count(*) from reviews").query(Integer.class).single();
    }
}
