package io.github.kerbiy.crate.gateway.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gives every request an X-Request-Id (SPEC 7): forwarded to the service and echoed on the
 * response. A WebFilter at highest precedence runs before Spring Security, so 401s carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter implements WebFilter {

    static final String HEADER = "X-Request-Id";

    // The id ends up in every service's logs, so a client-supplied one must look like an id:
    // no newlines or other characters that could forge log lines, and no unbounded length.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        // beforeCommit: set it last, so a header copied from the service's response can't duplicate it.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, id);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, id)))
                .build());
    }
}
