package io.github.kerbiy.crate.gateway.security;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Who may call what (SPEC 3.5). The gateway runs on WebFlux, so this is the reactive flavor of
 * Spring Security: ServerHttpSecurity / SecurityWebFilterChain instead of HttpSecurity / SecurityFilterChain.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    // SPEC 3.3: every synchronous call has explicit timeouts.
    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ServerCodecConfigurer codecs) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .anyExchange().authenticated())
                // Bearer token -> ReactiveJwtDecoder below -> JwtAuthenticationToken, or 401
                // (503 if user-service's keys can't be fetched).
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationFailureHandler(new AuthenticationUnavailableHandler(codecs)))
                // CSRF attacks ride on cookies the browser sends by itself. We use no cookies, only
                // an Authorization header the SPA sets explicitly, so CSRF protection only gets in
                // the way (it would 403 POST /api/auth/login).
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Stateless: every request proves itself with its token; no session is created or read.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .build();
    }

    /**
     * Verifies RS256 signatures with user-service's public keys, then checks exp/nbf (60 s clock
     * skew) and iss. The JWK Set is fetched on the first token, cached, and re-fetched when a token
     * names an unknown kid, so nothing here needs user-service to be up at startup.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) JWKS_TIMEOUT.toMillis())
                .responseTimeout(JWKS_TIMEOUT);
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build())
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        // A failed JWKS fetch surfaces as IllegalStateException("Could not obtain the keys"), which
        // Spring Security doesn't treat as an authentication failure: it would become a 500.
        // As a (non-"bad token") JwtException it becomes an AuthenticationServiceException instead,
        // which AuthenticationUnavailableHandler answers with 503.
        return token -> decoder.decode(token)
                .onErrorMap(IllegalStateException.class, SigningKeysUnavailableException::new);
    }
}
