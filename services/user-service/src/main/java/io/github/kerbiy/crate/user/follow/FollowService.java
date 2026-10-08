package io.github.kerbiy.crate.user.follow;

import io.github.kerbiy.crate.user.account.UserRepository;
import io.github.kerbiy.crate.user.follow.FollowRepository.FollowRow;
import io.github.kerbiy.crate.user.web.Problems;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Follow and unfollow, and the two lists. Both writes are idempotent (SPEC 7): repeating one is harmless. */
@Service
class FollowService {

    private final FollowRepository follows;
    private final UserRepository users;

    FollowService(FollowRepository follows, UserRepository users) {
        this.follows = follows;
        this.users = users;
    }

    void follow(UUID me, UUID target) {
        // Checked here for a clear 400; the table's check constraint is the guarantee.
        if (me.equals(target)) {
            throw Problems.exception(HttpStatus.BAD_REQUEST, "cannot-follow-self", "Can't follow yourself",
                    "You can't follow yourself.");
        }
        if (!users.existsById(target)) {
            throw Problems.userNotFound();
        }
        try {
            follows.follow(me, target);
        } catch (DataIntegrityViolationException e) {
            // A foreign key failed: the signed-in user no longer exists, or the target was deleted
            // between the check above and the insert.
            throw Problems.userNotFound();
        }
    }

    /** No checks needed: deleting a follow of an unknown user, or one that isn't there, deletes nothing. */
    void unfollow(UUID me, UUID target) {
        follows.unfollow(me, target);
    }

    FollowPage followers(UUID userId, FollowCursor cursor, int limit) {
        requireUser(userId);
        return page(follows.followers(userId, cursor, limit + 1), limit);
    }

    FollowPage following(UUID userId, FollowCursor cursor, int limit) {
        requireUser(userId);
        return page(follows.following(userId, cursor, limit + 1), limit);
    }

    /** Not paged: at friend scale it's a short list, and feed v1 needs all of it (SPEC 3.6). */
    FollowingIds followingIds(UUID userId) {
        return new FollowingIds(follows.followingIds(userId));
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw Problems.userNotFound();
        }
    }

    /** One row more than asked for was read: if it's there, there is a next page. */
    private static FollowPage page(List<FollowRow> rows, int limit) {
        List<FollowRow> items = rows.size() > limit ? rows.subList(0, limit) : rows;
        String next = rows.size() > limit
                ? new FollowCursor(items.getLast().followedAt(), items.getLast().user().id()).encode()
                : null;
        return new FollowPage(items.stream().map(FollowRow::user).toList(), next);
    }
}
