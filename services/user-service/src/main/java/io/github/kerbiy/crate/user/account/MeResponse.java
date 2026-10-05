package io.github.kerbiy.crate.user.account;

import java.time.Instant;
import java.util.UUID;

/** The signed-in user's own account. Includes the email, which public profiles won't. */
record MeResponse(UUID id, String username, String email, String displayName, Instant createdAt) {

    static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getCreatedAt());
    }
}
