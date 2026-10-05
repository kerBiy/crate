package io.github.kerbiy.crate.user.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Settings under {@code crate.auth}. Spring splits the comma-separated INVITE_CODES value into
 * the list, and turns {@code private-key-location} (e.g. {@code file:...}) into a Resource.
 */
@ConfigurationProperties("crate.auth")
public record AuthProperties(List<String> inviteCodes, Jwt jwt) {

    public AuthProperties {
        inviteCodes = inviteCodes == null ? List.of() : List.copyOf(inviteCodes);
    }

    public record Jwt(String issuer, Duration ttl, Resource privateKeyLocation) {
    }
}
