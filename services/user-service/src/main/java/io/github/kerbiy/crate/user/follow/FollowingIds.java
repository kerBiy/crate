package io.github.kerbiy.crate.user.follow;

import java.util.List;
import java.util.UUID;

/** Everyone a user follows, ids only: review-service builds feed v1 from it. */
record FollowingIds(List<UUID> ids) {
}
