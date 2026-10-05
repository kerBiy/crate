package io.github.kerbiy.crate.user.auth;

import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import io.github.kerbiy.crate.user.web.Problems;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

@Service
class  RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final List<String> inviteCodes;

    RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.inviteCodes = properties.inviteCodes().stream().map(String::strip).filter(code -> !code.isEmpty()).toList();
    }

    User register(RegisterRequest request) {
        if (!Passwords.fitsBcrypt(request.password())) {
            throw passwordTooLong();
        }
        // Checked before uniqueness, so without a valid code nobody can probe which names exist.
        if (!isValidInviteCode(request.inviteCode())) {
            throw Problems.exception(HttpStatus.FORBIDDEN, "invalid-invite-code",
                    "Invalid invite code", "The invite code is not valid.");
        }

        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        // Friendly, specific errors for the common case...
        if (users.existsByUsername(username)) {
            throw usernameTaken();
        }
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }

        try {
            return users.saveAndFlush(new User(username, email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // ...while the unique constraints are the real guarantee: two concurrent requests
            // can both pass the checks above, and then the second insert fails here.
            throw Problems.exception(HttpStatus.CONFLICT, "account-exists",
                    "Account already exists", "The username or email is already registered.");
        }
    }

    /** Constant-time comparison, so response time doesn't leak how much of a guess matched. */
    private boolean isValidInviteCode(String candidate) {
        byte[] candidateBytes = candidate.strip().getBytes(StandardCharsets.UTF_8);
        boolean valid = false;
        for (String code : inviteCodes) {
            valid |= MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), candidateBytes);
        }
        return valid;
    }

    private static ErrorResponseException usernameTaken() {
        return Problems.exception(HttpStatus.CONFLICT, "username-taken",
                "Username taken", "That username is already taken.");
    }

    private static ErrorResponseException emailTaken() {
        return Problems.exception(HttpStatus.CONFLICT, "email-taken",
                "Email already registered", "An account with that email already exists.");
    }

    /** Same shape as a Bean Validation failure, so clients handle both the same way. */
    private static ErrorResponseException passwordTooLong() {
        ErrorResponseException ex = Problems.exception(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation failed", "One or more fields are invalid.");
        ex.getBody().setProperty("errors", List.of(Map.of(
                "field", "password",
                "message", "must be at most " + Passwords.BCRYPT_MAX_BYTES + " bytes")));
        return ex;
    }
}
