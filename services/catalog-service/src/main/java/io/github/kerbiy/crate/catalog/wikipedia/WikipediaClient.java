package io.github.kerbiy.crate.catalog.wikipedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.RateLimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * An album's English Wikipedia intro (docs/adr/014-wikipedia-descriptions.md): the article's title from
 * its Wikidata item, then the article's summary from the Wikipedia REST API.
 *
 * <p>One attempt per call, no retry: nobody waits on a description, and the next view asks again.
 * Blocking and rate-limited like {@code MusicBrainzClient}; never call it inside a DB transaction.
 */
public class WikipediaClient {

    private static final int NOT_FOUND = 404;
    private static final String STANDARD_PAGE = "standard";

    private final RestClient wikidata;
    private final RestClient wikipedia;
    private final RateLimiter rateLimiter;

    WikipediaClient(RestClient wikidata, RestClient wikipedia, RateLimiter rateLimiter) {
        this.wikidata = wikidata;
        this.wikipedia = wikipedia;
        this.rateLimiter = rateLimiter;
    }

    /** Builds the client from configuration; tests pass a fake rate limiter. */
    public static WikipediaClient create(WikipediaProperties properties, String userAgent, RateLimiter rateLimiter) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                // The summary of a redirect page answers with a redirect to the article's own summary.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return new WikipediaClient(
                builder.clone().baseUrl(properties.wikidataBaseUrl().toString()).build(),
                builder.clone().baseUrl(properties.wikipediaBaseUrl().toString()).build(),
                rateLimiter);
    }

    /**
     * The English Wikipedia article's title of a Wikidata item ("Q202996" → "OK Computer"). Empty when the
     * item has no English article, or doesn't exist.
     */
    public Optional<String> englishTitle(String wikidataId) {
        return get(wikidata, uri -> uri.path("/w/api.php")
                        .queryParam("action", "wbgetentities")
                        .queryParam("ids", "{id}")
                        .queryParam("props", "sitelinks")
                        .queryParam("sitefilter", "enwiki")
                        .queryParam("format", "json")
                        .queryParam("formatversion", 2)
                        .build(Map.of("id", wikidataId)),
                EntitiesJson.class)
                .map(EntitiesJson::entities)
                .map(entities -> entities.get(wikidataId))
                .map(EntityJson::sitelinks)
                .map(sitelinks -> sitelinks.get("enwiki"))
                .map(SitelinkJson::title)
                .filter(title -> !title.isBlank());
    }

    /**
     * The article's intro as plain text, with its canonical address. Empty when there is no such article,
     * when it is a disambiguation page (about several things, not this album), or when it has no text.
     */
    public Optional<Summary> summary(String title) {
        // A URI variable, so the whole title is percent-encoded, "/" and "?" included ("Live/Dead").
        return get(wikipedia, uri -> uri.path("/api/rest_v1/page/summary/{title}").build(title.replace(' ', '_')),
                SummaryJson.class)
                .filter(json -> STANDARD_PAGE.equals(json.type()))
                .filter(json -> json.extract() != null && !json.extract().isBlank())
                .filter(json -> json.contentUrls() != null && json.contentUrls().desktop() != null
                        && json.contentUrls().desktop().page() != null)
                .map(json -> new Summary(json.title(), json.extract().strip(),
                        URI.create(json.contentUrls().desktop().page())));
    }

    /** One attempt: 2xx is the body, 404 is empty, anything else is a {@link WikipediaException}. */
    private <T> Optional<T> get(RestClient client, Function<UriBuilder, URI> uri, Class<T> type) {
        try {
            rateLimiter.acquire();
        } catch (MusicBrainzException e) {
            // The shared limiter class names MusicBrainz in its exception; here it was Wikimedia's queue.
            throw new WikipediaException("Wikimedia rate limit queue is full", e);
        }
        try {
            return client.get().uri(uri).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (response.getStatusCode().is2xxSuccessful()) {
                    return Optional.ofNullable(response.bodyTo(type));
                }
                if (status == NOT_FOUND) {
                    return Optional.empty();
                }
                throw new WikipediaException("Wikimedia answered " + status + " for " + request.getURI().getPath());
            });
        } catch (RestClientException e) {
            // Timeouts, refused connections, unreadable JSON.
            throw new WikipediaException("Wikimedia request failed: " + e.getMessage(), e);
        }
    }

    /**
     * An article's intro.
     *
     * @param title   as Wikipedia displays it, e.g. "OK Computer"
     * @param extract the first paragraph, plain text
     * @param url     the article, for the credit the license asks for
     */
    public record Summary(String title, String extract, URI url) {
    }

    record EntitiesJson(Map<String, EntityJson> entities) {
    }

    /** {@code sitelinks} is absent for a missing item, and empty for one without an English article. */
    record EntityJson(Map<String, SitelinkJson> sitelinks) {
    }

    record SitelinkJson(String title) {
    }

    /** {@code type}: "standard" for an article, "disambiguation", "no-extract" or "mainpage" otherwise. */
    record SummaryJson(String type, String title, String extract, @JsonProperty("content_urls") ContentUrlsJson contentUrls) {
    }

    record ContentUrlsJson(PageUrlsJson desktop) {
    }

    record PageUrlsJson(String page) {
    }
}
