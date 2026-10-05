package io.github.kerbiy.crate.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Makes the gateway the only writer of identity headers (SPEC 3.5, step 4). Services trust
 * X-User-Id blindly until Phase 3, so whatever a client sent is dropped on every route, public
 * ones included, and only a validated token puts the headers back.
 *
 * <p>A GlobalFilter runs on every routed request, after the security filter chain, so the
 * validated token is already in the exchange.
 */
@Component
class IdentityHeadersFilter implements GlobalFilter {

    static final String USER_ID = "X-User-Id";
    static final String USERNAME = "X-Username";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(authentication -> withIdentity(exchange, authentication.getToken()))
                .switchIfEmpty(Mono.fromSupplier(() -> withIdentity(exchange, null)))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt token) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    // remove() is case-insensitive and drops every value, so x-user-id or a
                    // repeated header can't sneak past.
                    headers.remove(USER_ID);
                    headers.remove(USERNAME);
                    if (token != null) {
                        setIfPresent(headers, USER_ID, token.getSubject());
                        setIfPresent(headers, USERNAME, token.getClaimAsString("username"));
                    }
                }))
                .build();
    }

    private static void setIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null) {
            headers.set(name, value);
        }
    }
}
