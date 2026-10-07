package io.github.kerbiy.crate.catalog.search;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.CatalogIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Its own Spring context: only here is the number of checked albums lowered, to see the cap work. */
@TestPropertySource(properties = "crate.catalog.search.cover-check-count=3")
class SearchCoverCheckLimitTests extends CatalogIntegrationTest {

    @Test
    void onlyTheTopOfTheRankingIsChecked() {
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", notContaining("arid:"))
                .willReturn(fixture("search-radiohead.json")));
        musicBrainz.stubFor(get(urlPathEqualTo(MB_SEARCH)).withQueryParam("query", containing("arid:"))
                .willReturn(fixture("artist-albums-radiohead.json")));

        search();

        musicBrainz.verify(3, anyRequestedFor(urlPathMatching(CAA + "/.*")));
        assertThat(jdbc.sql("select title from albums where has_cover order by release_count desc")
                .query(String.class).list()).containsExactly("OK Computer", "Pablo Honey", "The Bends");
        // The rest is unknown, not hidden.
        assertThat(count("search_results")).isGreaterThan(3);
    }

    private void search() {
        client.get().uri("/albums/search?q=radiohead").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items.length()").value(Integer.class, n -> assertThat(n).isGreaterThan(3));
    }
}
