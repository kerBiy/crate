package io.github.kerbiy.crate.user.profile;

import java.util.UUID;

/** A public profile: who they are, their follow counts, and whether the viewer follows them. No email. */
record Profile(UUID id, String username, String displayName, long followerCount, long followingCount,
        boolean followedByMe) {
}
