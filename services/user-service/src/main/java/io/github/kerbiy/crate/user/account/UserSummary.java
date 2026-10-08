package io.github.kerbiy.crate.user.account;

import java.util.UUID;

/** What other people may see of a user next to their activity: no email. */
public record UserSummary(UUID id, String username, String displayName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
