package io.github.kerbiy.crate.review.users;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks user-service who a user follows, for feed v1 (fan-out on read, SPEC 3.6). The second of the
 * two sync calls review-service makes (SPEC 3.3), with the same explicit timeout. Any failure means
 * "can't tell": the feed answers 503 rather than pretending you follow nobody.
 */
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient restClient;

    UserClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static UserClient create(UserProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new UserClient(restClient);
    }

    /**
     * @throws UserServiceUnavailableException any non-2xx answer, a timeout, no connection, or a body
     *         we can't read
     */
    public List<UUID> followingIds(UUID userId) {
        FollowingIds body;
        try {
            // retrieve() throws on 4xx/5xx; every error status means "can't tell" here.
            body = restClient.get().uri("/users/{id}/following/ids", userId).retrieve().body(FollowingIds.class);
        } catch (RestClientException e) {
            log.warn("user-service unavailable while loading who {} follows: {}", userId, e.getMessage());
            throw new UserServiceUnavailableException(e);
        }
        if (body == null || body.ids() == null) {
            log.warn("user-service sent no following ids for {}", userId);
            throw new UserServiceUnavailableException(null);
        }
        return body.ids();
    }

    /** user-service's answer. */
    record FollowingIds(List<UUID> ids) {
    }
}
