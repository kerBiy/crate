package io.github.kerbiy.crate.review.users;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code crate.users}. */
@ConfigurationProperties("crate.users")
public record UserProperties(URI baseUrl, Duration timeout) {

    public UserProperties {
        // Fail at startup, not on the first feed load.
        if (baseUrl == null) {
            throw new IllegalArgumentException("crate.users.base-url must be set");
        }
        if (timeout == null) {
            throw new IllegalArgumentException("crate.users.timeout must be set");
        }
    }
}
