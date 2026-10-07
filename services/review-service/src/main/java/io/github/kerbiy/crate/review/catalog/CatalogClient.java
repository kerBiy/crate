package io.github.kerbiy.crate.review.catalog;

import java.net.http.HttpClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks catalog-service whether an album exists (SPEC 3.3: the one sync call review-service needs
 * in the MVP). Only a 404 means "doesn't exist". Anything else that isn't a success (a 5xx, a
 * timeout, catalog down) means "can't tell", and is never treated as "doesn't exist".
 */
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    CatalogClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static CatalogClient create(CatalogProperties properties) {
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
        return new CatalogClient(restClient);
    }

    /**
     * Returns if the album exists.
     *
     * @throws AlbumNotFoundException catalog answered 404
     * @throws CatalogUnavailableException catalog couldn't answer: any other status, a timeout or no connection
     */
    public void requireAlbum(UUID albumId) {
        HttpStatus status;
        try {
            // exchange(): we read the status ourselves instead of letting RestClient throw on 4xx/5xx.
            status = restClient.get().uri("/albums/{id}", albumId)
                    .exchange((request, response) -> HttpStatus.resolve(response.getStatusCode().value()), true);
        } catch (RestClientException e) {
            // Timeouts and connection errors arrive here (as ResourceAccessException).
            log.warn("catalog-service unreachable while checking album {}: {}", albumId, e.getMessage());
            throw new CatalogUnavailableException(e);
        }
        if (status != null && status.is2xxSuccessful()) {
            return;
        }
        if (status == HttpStatus.NOT_FOUND) {
            throw new AlbumNotFoundException(albumId);
        }
        log.warn("catalog-service answered {} while checking album {}", status, albumId);
        throw new CatalogUnavailableException(null);
    }
}
