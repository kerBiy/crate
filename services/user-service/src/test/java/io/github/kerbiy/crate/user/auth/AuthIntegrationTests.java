package io.github.kerbiy.crate.user.auth;

import static io.github.kerbiy.crate.user.TestcontainersConfiguration.INVITE_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.user.TestcontainersConfiguration;
import io.github.kerbiy.crate.user.account.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @LocalServerPort
    int port;

    @Test
    void registerCreatesAccountWithHashedPassword() {
        String username = uniqueUsername();

        Map<String, Object> body = register(username, username + "@example.com", PASSWORD, INVITE_CODE)
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();

        assertThat(body).containsEntry("username", username).containsKey("id");
        var stored = users.findByUsername(username).orElseThrow();
        assertThat(stored.getId().toString()).isEqualTo(body.get("id"));
        assertThat(stored.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerNormalizesUsernameAndEmailToLowercase() {
        String username = uniqueUsername();

        register(username.toUpperCase(), username.toUpperCase() + "@Example.COM", PASSWORD, INVITE_CODE)
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.username").isEqualTo(username);

        assertThat(users.findByEmail(username + "@example.com")).isPresent();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        String username = uniqueUsername();
        register(username, username + "@example.com", PASSWORD, INVITE_CODE).expectStatus().isCreated();

        register(username, "other-" + username + "@example.com", PASSWORD, INVITE_CODE)
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:username-taken")
                .jsonPath("$.status").isEqualTo(409);
    }

    @Test
    void registerRejectsWrongInviteCode() {
        String username = uniqueUsername();

        register(username, username + "@example.com", PASSWORD, "not-the-code")
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:invalid-invite-code");

        assertThat(users.existsByUsername(username)).isFalse();
    }

    @Test
    void registerRejectsInvalidFieldsWithFieldErrors() {
        register("x", "not-an-email", "short", INVITE_CODE)
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:validation-failed")
                .jsonPath("$.errors.length()").isEqualTo(3)
                .jsonPath("$.errors[?(@.field == 'password')].message").isEqualTo("must be at least 10 characters");
    }

    @Test
    void loginWithUsernameOrEmailReturnsToken() {
        String username = registerUser();

        for (String login : new String[] {username, username + "@example.com"}) {
            login(login, PASSWORD)
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isNotEmpty()
                    .jsonPath("$.expiresAt").isNotEmpty();
        }
    }

    @Test
    void loginRejectsWrongPassword() {
        String username = registerUser();

        login(username, "wrong password!")
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:crate:problem:invalid-credentials");
    }

    @Test
    void loginGivesSameErrorForUnknownAccountAsForWrongPassword() {
        String username = registerUser();

        String wrongPassword = login(username, "wrong password!")
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();
        String unknownAccount = login("nobody_" + username, "wrong password!")
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();

        // `instance` is the request path, the same for both; everything else must match too.
        assertThat(unknownAccount).isEqualTo(wrongPassword);
    }

    @Test
    void issuedTokenVerifiesAgainstJwksEndpoint() {
        String username = registerUser();
        UUID id = users.findByUsername(username).orElseThrow().getId();

        Map<String, Object> response = login(username, PASSWORD)
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();
        String token = (String) response.get("accessToken");

        // What the gateway will do: fetch the public keys over HTTP and verify the signature,
        // expiry and issuer. decode() throws if any of that fails.
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("crate-user-service"));
        Jwt jwt = decoder.decode(token);

        assertThat(String.valueOf(jwt.getHeaders().get("alg"))).isEqualTo("RS256");
        assertThat(jwt.getHeaders()).containsKey("kid");
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo(username);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(12));
        assertThat(response.get("expiresAt")).isEqualTo(jwt.getExpiresAt().toString());
    }

    @Test
    void jwksExposesOnlyThePublicKey() {
        client.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys.length()").isEqualTo(1)
                .jsonPath("$.keys[0].kty").isEqualTo("RSA")
                .jsonPath("$.keys[0].kid").isNotEmpty()
                .jsonPath("$.keys[0].n").isNotEmpty()
                .jsonPath("$.keys[0].e").isNotEmpty()
                // RSA private members (RFC 7518 section 6.3.2) must never be published.
                .jsonPath("$.keys[0].d").doesNotExist()
                .jsonPath("$.keys[0].p").doesNotExist()
                .jsonPath("$.keys[0].q").doesNotExist()
                .jsonPath("$.keys[0].dp").doesNotExist()
                .jsonPath("$.keys[0].dq").doesNotExist()
                .jsonPath("$.keys[0].qi").doesNotExist();
    }

    private String registerUser() {
        String username = uniqueUsername();
        register(username, username + "@example.com", PASSWORD, INVITE_CODE).expectStatus().isCreated();
        return username;
    }

    private RestTestClient.ResponseSpec register(String username, String email, String password, String inviteCode) {
        return client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", email, "password", password, "inviteCode", inviteCode))
                .exchange();
    }

    private RestTestClient.ResponseSpec login(String login, String password) {
        return client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("login", login, "password", password))
                .exchange();
    }

    private static String uniqueUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
