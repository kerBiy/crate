package io.github.kerbiy.crate.user.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finding users: by a batch of ids, so lists can show names with one call, and by name (SPEC 5.2). */
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

    /** {@code GET /users/search?q=}: username or display name containing q, case-insensitive. */
    @GetMapping("/users/search")
    UserList search(@RequestParam @NotBlank @Size(max = 50) String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        String text = q.strip().toLowerCase(Locale.ROOT);
        String literal = escapeLike(text);
        return new UserList(users.search(text, "%" + literal + "%", literal + "%", limit).stream()
                .map(UserSummary::from)
                .toList());
    }

    /** "50%_off" must match those characters, not "anything": escape LIKE's wildcards (and the escape itself). */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
