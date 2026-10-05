package io.github.kerbiy.crate.user.auth;

import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import io.github.kerbiy.crate.user.auth.token.TokenIssuer;
import io.github.kerbiy.crate.user.web.Problems;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

@Service
class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    /**
     * Hash of a random throwaway password. When the account doesn't exist we still check the
     * password against it, so "no such user" takes as long as "wrong password".
     */
    private final String dummyHash;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    Jwt login(LoginRequest request) {
        // Longer than BCrypt accepts, so it can't be anyone's password.
        if (!Passwords.fitsBcrypt(request.password())) {
            throw invalidCredentials();
        }
        Optional<User> user = findByLogin(request.login());
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw invalidCredentials();
        }
        return tokenIssuer.issue(user.get());
    }

    private Optional<User> findByLogin(String login) {
        String normalized = login.strip().toLowerCase(Locale.ROOT);
        return normalized.contains("@") ? users.findByEmail(normalized) : users.findByUsername(normalized);
    }

    /** One response for every failure: callers can't tell an unknown account from a wrong password. */
    private static ErrorResponseException invalidCredentials() {
        return Problems.exception(HttpStatus.UNAUTHORIZED, "invalid-credentials",
                "Invalid credentials", "The login or password is incorrect.");
    }
}
