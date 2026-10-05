package io.github.kerbiy.crate.gateway.security;

import org.springframework.security.oauth2.jwt.JwtException;

/**
 * The gateway couldn't get user-service's public keys (down, slow, or a bad response), so it can't
 * tell whether a token is valid. The server's problem, not the client's: answered with 503, not 401.
 */
class SigningKeysUnavailableException extends JwtException {

    SigningKeysUnavailableException(Throwable cause) {
        super("Could not obtain the JWT signing keys", cause);
    }
}
