package io.github.kerbiy.crate.user.account;

import io.github.kerbiy.crate.user.web.Problems;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user. Identity comes from X-User-Id, which only the gateway sets, after validating
 * the JWT (SPEC 3.5). Trusting it is a known shortcut until Phase 3 (SPEC 3.6).
 */
@RestController
class MeController {

    private final UserRepository users;

    MeController(UserRepository users) {
        this.users = users;
    }

    // Not required = true: a missing header means "not signed in" (401), not a malformed request (400).
    @GetMapping("/users/me")
    MeResponse me(@RequestHeader(name = "X-User-Id", required = false) UUID userId) {
        if (userId == null) {
            throw Problems.exception(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthenticated",
                    "This endpoint needs a signed-in user.");
        }
        // A valid token for a user that no longer exists.
        User user = users.findById(userId).orElseThrow(() -> Problems.exception(HttpStatus.NOT_FOUND,
                "user-not-found", "User not found", "The signed-in user doesn't exist."));
        return MeResponse.from(user);
    }
}
