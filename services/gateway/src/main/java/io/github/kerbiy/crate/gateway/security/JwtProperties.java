package io.github.kerbiy.crate.gateway.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code crate.gateway.jwt}: whose tokens we accept and where their public keys live. */
@ConfigurationProperties("crate.gateway.jwt")
record JwtProperties(String issuer, URI jwkSetUri) {
}
