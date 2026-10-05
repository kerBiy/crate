package io.github.kerbiy.crate.user.auth.token;

import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.auth.AuthProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Mints access tokens (SPEC 3.5): RS256, claims sub, username, iss, iat, exp. */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final AuthProperties.Jwt settings;

    TokenIssuer(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.settings = properties.jwt();
    }

    public Jwt issue(User user) {
        // JWT times are whole seconds; truncating keeps the response's expiresAt equal to exp.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(settings.ttl()))
                .build();
        // The encoder adds the signing key's kid to the header.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }
}
