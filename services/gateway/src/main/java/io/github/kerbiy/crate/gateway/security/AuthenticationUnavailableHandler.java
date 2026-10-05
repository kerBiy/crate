package io.github.kerbiy.crate.gateway.security;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Decides what a failed token check returns. Missing signing keys → 503 Problem Details; anything
 * else goes to Spring's default handler (401 + WWW-Authenticate for a bad or expired token).
 */
class AuthenticationUnavailableHandler implements ServerAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationUnavailableHandler.class);

    private final ServerAuthenticationFailureHandler defaultHandler =
            new ServerAuthenticationEntryPointFailureHandler(new BearerTokenServerAuthenticationEntryPoint());
    private final ServerResponse.Context responseContext;

    AuthenticationUnavailableHandler(ServerCodecConfigurer codecs) {
        // WebFlux's own message writers, so ProblemDetail is serialized like in a controller.
        List<HttpMessageWriter<?>> writers = codecs.getWriters();
        this.responseContext = new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return writers;
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return List.of();
            }
        };
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        if (!(exception.getCause() instanceof SigningKeysUnavailableException keysUnavailable)) {
            return defaultHandler.onAuthenticationFailure(webFilterExchange, exception);
        }
        ServerWebExchange exchange = webFilterExchange.getExchange();
        // The details (host, port, connection error) go to our logs, never to the client.
        log.warn("Cannot verify token for {}: {}", exchange.getRequest().getPath(),
                NestedExceptionUtils.getMostSpecificCause(keysUnavailable).toString());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Sign-in can't be checked right now. Please try again shortly.");
        problem.setType(URI.create("urn:crate:problem:authentication-unavailable"));
        problem.setTitle("Authentication unavailable");
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, responseContext));
    }
}
