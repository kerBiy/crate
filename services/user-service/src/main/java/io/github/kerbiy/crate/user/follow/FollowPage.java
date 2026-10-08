package io.github.kerbiy.crate.user.follow;

import io.github.kerbiy.crate.user.account.UserSummary;
import java.util.List;

/** One page of followers or following (SPEC 7). nextCursor is null on the last page. */
record FollowPage(List<UserSummary> items, String nextCursor) {
}
