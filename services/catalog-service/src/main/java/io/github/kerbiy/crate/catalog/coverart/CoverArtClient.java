package io.github.kerbiy.crate.catalog.coverart;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Asks the Cover Art Archive whether a release group has a front cover, as cheaply as possible:
 * {@code HEAD /release-group/{id}/front-250}. CAA answers 307 (a redirect to the image on
 * archive.org) when there is one and 404 when there isn't. The redirect is not followed: the image
 * itself takes seconds to fetch, and its existence is all we want.
 */
public class CoverArtClient implements CoverSource {

    private static final int NOT_FOUND = 404;

    private final HttpClient http;
    private final String baseUrl;
    private final String userAgent;
    private final Duration timeout;

    CoverArtClient(HttpClient http, URI baseUrl, String userAgent, Duration timeout) {
        this.http = http;
        // Concatenated, not URI.resolve: an absolute path would drop a base path such as /caa in tests.
        this.baseUrl = baseUrl.toString().replaceAll("/+$", "");
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    public static CoverArtClient create(CoverArtProperties properties, String userAgent) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new CoverArtClient(http, properties.baseUrl(), userAgent, properties.timeout());
    }

    /** PRESENT on 2xx/3xx, ABSENT on 404, UNKNOWN on anything else (5xx, 429, timeout, no network). */
    @Override
    public Cover check(UUID releaseGroupId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/release-group/" + releaseGroupId + "/front-250"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", userAgent)
                .timeout(timeout)
                .build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == NOT_FOUND) {
                return Cover.ABSENT;
            }
            return status >= 200 && status < 400 ? Cover.PRESENT : Cover.UNKNOWN;
        } catch (IOException e) {
            return Cover.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Cover.UNKNOWN;
        }
    }
}
