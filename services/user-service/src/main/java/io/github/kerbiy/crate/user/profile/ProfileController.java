package io.github.kerbiy.crate.user.profile;

import io.github.kerbiy.crate.user.account.User;
import io.github.kerbiy.crate.user.account.UserRepository;
import io.github.kerbiy.crate.user.follow.FollowRepository;
import io.github.kerbiy.crate.user.web.Problems;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /users/{username}}: the profile page header. Spring prefers literal paths, so
 * {@code /users/me} and {@code /users/search} never land here; registration reserves those names.
 */
@RestController
class ProfileController {

    private final UserRepository users;
    private final FollowRepository follows;

    ProfileController(UserRepository users, FollowRepository follows) {
        this.users = users;
        this.follows = follows;
    }

    @GetMapping("/users/{username}")
    Profile profile(@PathVariable String username,
            @RequestHeader(name = "X-User-Id", required = false) UUID viewerId) {
        // Usernames are stored lowercase, so /u/Ana finds ana.
        User user = users.findByUsername(username.toLowerCase(Locale.ROOT)).orElseThrow(Problems::userNotFound);
        boolean followedByMe = viewerId != null && !viewerId.equals(user.getId())
                && follows.exists(viewerId, user.getId());
        return new Profile(user.getId(), user.getUsername(), user.getDisplayName(),
                follows.countFollowers(user.getId()), follows.countFollowing(user.getId()), followedByMe);
    }
}
