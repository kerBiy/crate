package io.github.kerbiy.crate.user.account;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Batch lookup, so a list of reviews or feed items can show names with one call (SPEC 5.2). */
@RestController
class UserLookupController {

    private final UserRepository users;

    UserLookupController(UserRepository users) {
        this.users = users;
    }

    /** {@code GET /users?ids=a,b,c}: request order, unknown ids left out. Spring splits the list. */
    @GetMapping("/users")
    UserList getMany(@RequestParam @NotEmpty @Size(max = 100) List<UUID> ids) {
        Map<UUID, User> found = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new UserList(ids.stream().distinct()
                .map(found::get)
                .filter(Objects::nonNull)
                .map(UserSummary::from)
                .toList());
    }
}
