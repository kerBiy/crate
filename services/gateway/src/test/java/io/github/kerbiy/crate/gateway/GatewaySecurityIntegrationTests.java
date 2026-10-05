package io.github.kerbiy.crate.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The gateway in front of a WireMock "user-service" that serves a JWK Set and records what the
 * gateway forwards. Tokens are minted with a throwaway RSA key (TestTokens), like user-service would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTests {

    private static final String KID = "test-key";
    private static final String JWKS_PATH = "/.well-known/jwks.json";

    private static final RSAKey SIGNING_KEY = TestTokens.rsaKey(KID);
    // Same kid, different key pair: the decoder finds "its" key and the signature check fails.
    private static final RSAKey FORGED_KEY = TestTokens.rsaKey(KID);

    @RegisterExtension
    static WireMockExtension services = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("crate.services.user-service", services::baseUrl);
        registry.add("crate.services.catalog-service", services::baseUrl);
        registry.add("crate.services.review-service", services::baseUrl);
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void stubServices() {
        // Only the public half is published, like user-service's JwksController.
        services.stubFor(get(JWKS_PATH).willReturn(okJson(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
        services.stubFor(any(anyUrl())
                .atPriority(10)
                .willReturn(okJson("{}")));
    }

    @Test
    void protectedRouteWithoutTokenIs401AndNeverReachesTheService() {
        client.get().uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*");

        services.verify(0, getRequestedFor(urlEqualTo("/users/me")));
    }

    @Test
    void tokenWithInvalidSignatureIs401() {
        String forged = TestTokens.mint(FORGED_KEY, UUID.randomUUID(), "mallory", Instant.now(), Duration.ofHours(12));

        client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth(forged))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, ".*invalid_token.*");

        services.verify(0, getRequestedFor(urlEqualTo("/users/me")));
    }

    @Test
    void expiredTokenIs401() {
        // Expired an hour ago: well past the decoder's 60 s clock-skew allowance.
        Instant issuedAt = Instant.now().minus(Duration.ofHours(13));
        String expired = TestTokens.mint(SIGNING_KEY, UUID.randomUUID(), "alice", issuedAt, Duration.ofHours(12));

        client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth(expired))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, ".*invalid_token.*");

        services.verify(0, getRequestedFor(urlEqualTo("/users/me")));
    }

    @Test
    void publicAuthRouteWorksWithoutTokenAndStripsApiPrefix() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"alice\",\"password\":\"irrelevant\"}")
                .exchange()
                .expectStatus().isOk();

        services.verify(1, postRequestedFor(urlEqualTo("/auth/login")));
    }

    @Test
    void validTokenReachesServiceWithIdentityHeaders() {
        UUID userId = UUID.randomUUID();

        client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth(validToken(userId, "alice")))
                .exchange()
                .expectStatus().isOk();

        services.verify(1, getRequestedFor(urlEqualTo("/users/me"))
                .withHeader("X-User-Id", equalTo(userId.toString()))
                .withHeader("X-Username", equalTo("alice")));
    }

    @Test
    void spoofedIdentityHeadersAreReplacedByTheTokensIdentity() {
        UUID userId = UUID.randomUUID();

        client.get().uri("/api/users/me")
                .headers(h -> {
                    h.setBearerAuth(validToken(userId, "alice"));
                    h.add("X-User-Id", UUID.randomUUID().toString());
                    // Different casing: header names are case-insensitive, the strip must be too.
                    h.add("x-username", "mallory");
                })
                .exchange()
                .expectStatus().isOk();

        LoggedRequest forwarded = singleRequestTo("/users/me");
        assertThat(forwarded.getHeaders().getHeader("X-User-Id").values()).containsExactly(userId.toString());
        assertThat(forwarded.getHeaders().getHeader("X-Username").values()).containsExactly("alice");
    }

    @Test
    void spoofedIdentityHeadersAreRemovedOnPublicRoutes() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Username", "mallory")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        services.verify(1, postRequestedFor(urlEqualTo("/auth/login"))
                .withoutHeader("X-User-Id")
                .withoutHeader("X-Username"));
    }

    @Test
    void requestIdIsGeneratedWhenMissing() {
        String returned = client.get().uri("/api/users/me")
                .headers(h -> h.setBearerAuth(validToken(UUID.randomUUID(), "alice")))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class).getResponseHeaders().getFirst("X-Request-Id");

        assertThat(returned).isNotBlank();
        services.verify(1, getRequestedFor(urlEqualTo("/users/me")).withHeader("X-Request-Id", equalTo(returned)));
    }

    @Test
    void incomingRequestIdIsForwardedUnchanged() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "client-abc-123")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "client-abc-123");

        services.verify(1, postRequestedFor(urlEqualTo("/auth/login"))
                .withHeader("X-Request-Id", equalTo("client-abc-123")));
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() {
        String returned = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "x".repeat(65))
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class).getResponseHeaders().getFirst("X-Request-Id");

        assertThat(returned).isNotEqualTo("x".repeat(65));
        assertThat(UUID.fromString(returned)).isNotNull();
    }

    @Test
    void unauthorizedResponsesCarryARequestIdToo() {
        client.get().uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void jwksIsNotReachableThroughTheGateway() {
        String token = validToken(UUID.randomUUID(), "alice");

        // No route matches: the path isn't under any /api/<service>/ prefix.
        client.get().uri("/api" + JWKS_PATH)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isNotFound();
        // Path traversal through the public route. The WebFlux firewall rejects non-normalized paths.
        client.get().uri("/api/auth/.." + JWKS_PATH)
                .exchange()
                .expectStatus().isBadRequest();
        client.get().uri("/api/auth/%2e%2e" + JWKS_PATH)
                .exchange()
                .expectStatus().isBadRequest();

        // The decoder fetches the JWKS itself (no X-Request-Id); a proxied request would carry one.
        services.verify(0, anyRequestedFor(urlPathEqualTo(JWKS_PATH)).withHeader("X-Request-Id", matching(".*")));
    }

    private LoggedRequest singleRequestTo(String url) {
        List<LoggedRequest> requests = services.findAll(getRequestedFor(urlEqualTo(url)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private static String validToken(UUID userId, String username) {
        return TestTokens.mint(SIGNING_KEY, userId, username, Instant.now(), Duration.ofHours(12));
    }
}
