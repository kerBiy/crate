package io.github.kerbiy.crate.user.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Users whose username or display name contains the text (case-insensitive). {@code pattern} and
     * {@code prefix} are LIKE patterns with {@code %}, {@code _} and {@code \} already escaped.
     * Exact username first, then usernames starting with the text, then the rest.
     * A sequential scan: fine for a friends app (SPEC 5.2).
     */
    @NativeQuery("""
            select * from users
            where username ilike :pattern escape '\\' or display_name ilike :pattern escape '\\'
            order by username = :exact desc, username like :prefix escape '\\' desc, username
            limit :limit""")
    List<User> search(String exact, String pattern, String prefix, int limit);
}
