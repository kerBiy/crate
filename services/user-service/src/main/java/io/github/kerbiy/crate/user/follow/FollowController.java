package io.github.kerbiy.crate.user.follow;

import io.github.kerbiy.crate.user.web.Problems;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The follow graph (SPEC 5.2). Identity comes from X-User-Id, which only the gateway sets after
 * validating the JWT (SPEC 3.5). Trusting it is a known shortcut until Phase 3 (SPEC 3.6).
 */
@RestController
class FollowController {

    private static final String USER_ID = "X-User-Id";

    private final FollowService follows;

    FollowController(FollowService follows) {
        this.follows = follows;
    }

    /** PUT, not POST: "I follow {id}" is a state, so sending it twice is the same as once. */
    @PutMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void follow(@RequestHeader(name = USER_ID, required = false) UUID userId, @PathVariable UUID id) {
        follows.follow(Problems.signedIn(userId), id);
    }

    @DeleteMapping("/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfollow(@RequestHeader(name = USER_ID, required = false) UUID userId, @PathVariable UUID id) {
        follows.unfollow(Problems.signedIn(userId), id);
    }

    @GetMapping("/users/{id}/followers")
    FollowPage followers(@PathVariable UUID id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return follows.followers(id, parse(cursor), limit);
    }

    @GetMapping("/users/{id}/following")
    FollowPage following(@PathVariable UUID id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return follows.following(id, parse(cursor), limit);
    }

    /**
     * Internal, for review-service's feed v1. Also reachable through the gateway, which is fine: it
     * shows nothing that {@code /following} doesn't.
     */
    @GetMapping("/users/{id}/following/ids")
    FollowingIds followingIds(@PathVariable UUID id) {
        return follows.followingIds(id);
    }

    private static FollowCursor parse(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            return FollowCursor.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw Problems.invalidField("cursor", "is not a valid cursor");
        }
    }
}
