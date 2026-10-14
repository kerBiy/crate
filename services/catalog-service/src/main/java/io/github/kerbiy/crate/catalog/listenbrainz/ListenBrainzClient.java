package io.github.kerbiy.crate.catalog.listenbrainz;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.RateLimiter;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Popularity from ListenBrainz (docs/adr/012-listenbrainz-popularity.md): how many ListenBrainz users
 * listened to each release group, and how often, keyed by the same MBIDs MusicBrainz uses.
 *
 * <p>One call, no retry: search is waiting, and without counts it simply ranks as before. Blocking
 * and rate-limited like {@code MusicBrainzClient}; never call it inside a DB transaction.
 */
public class ListenBrainzClient {

    /** The most MBIDs the popularity endpoint accepts per call (MAX_ITEMS_PER_GET on the server). */
    static final int MAX_IDS = 1000;

    private final RestClient restClient;
    private final RateLimiter rateLimiter;

    ListenBrainzClient(RestClient restClient, RateLimiter rateLimiter) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
    }

    /** Builds the client from configuration; tests pass a fake rate limiter. */
    public static ListenBrainzClient create(ListenBrainzProperties properties, String userAgent, RateLimiter rateLimiter) {
        // HTTP/1.1: over plain http (tests) the JDK client would try an HTTP/2 upgrade on a POST with a body,
        // which servers may reset; one small call per second gains nothing from HTTP/2 anyway.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                // Buffered, so the body goes out with a Content-Length: ListenBrainz answers 400 to a
                // chunked body, which is how the JDK factory streams one.
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new ListenBrainzClient(restClient, rateLimiter);
    }

    /**
     * Listeners and listens per release group. Every requested id is in the answer; zeros also mean
     * "ListenBrainz doesn't know it" (it answers nulls then).
     *
     * @throws ListenBrainzException on any failure, including a full rate-limit queue
     */
    public Map<UUID, Popularity> popularity(Collection<UUID> releaseGroupIds) {
        List<UUID> ids = releaseGroupIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new IllegalArgumentException("At most " + MAX_IDS + " release groups per call, got " + ids.size());
        }
        try {
            rateLimiter.acquire();
        } catch (MusicBrainzException e) {
            // The shared limiter class names MusicBrainz in its exception; here it was ListenBrainz's queue.
            throw new ListenBrainzException("ListenBrainz rate limit queue is full", e);
        }

        List<PopularityJson> answer;
        try {
            answer = restClient.post()
                    .uri("/1/popularity/release-group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PopularityRequest(ids))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PopularityJson>>() { });
        } catch (RestClientException e) {
            // Timeouts, refused connections, 4xx/5xx (429 included), unreadable JSON.
            throw new ListenBrainzException("ListenBrainz request failed: " + e.getMessage(), e);
        }

        Map<UUID, Popularity> counts = new HashMap<>();
        ids.forEach(id -> counts.put(id, Popularity.UNKNOWN));
        if (answer != null) {
            answer.stream()
                    .filter(json -> json.releaseGroupMbid() != null && counts.containsKey(json.releaseGroupMbid()))
                    .forEach(json -> counts.put(json.releaseGroupMbid(), new Popularity(
                            json.totalUserCount() == null ? 0 : json.totalUserCount(),
                            json.totalListenCount() == null ? 0 : json.totalListenCount())));
        }
        return Map.copyOf(counts);
    }

    record PopularityRequest(@JsonProperty("release_group_mbids") List<UUID> releaseGroupMbids) {
    }

    /** One entry per requested MBID; the counts are null when ListenBrainz has no listens for it. */
    record PopularityJson(
            @JsonProperty("release_group_mbid") UUID releaseGroupMbid,
            @JsonProperty("total_listen_count") Long totalListenCount,
            @JsonProperty("total_user_count") Integer totalUserCount) {
    }
}
