package io.github.kerbiy.crate.user.follow;

import io.github.kerbiy.crate.user.account.UserSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The follows table, in plain SQL: a follow is just a pair of ids, so it has no JPA entity.
 * Lists are keyset-paginated, newest follow first: {@code (created_at, user id) < cursor} matches
 * the order of follows_followee_idx / follows_follower_idx, so Postgres seeks to the cursor.
 */
@Repository
public class FollowRepository {

    private final JdbcClient jdbc;

    FollowRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent: following someone you already follow changes nothing. {@code on conflict do nothing}
     * also covers two requests racing each other: both succeed, one row exists.
     */
    void follow(UUID followerId, UUID followeeId) {
        jdbc.sql("""
                insert into follows (follower_id, followee_id) values (:follower, :followee)
                on conflict (follower_id, followee_id) do nothing""")
                .param("follower", followerId)
                .param("followee", followeeId)
                .update();
    }

    /** Idempotent: deleting a follow that isn't there deletes nothing. */
    void unfollow(UUID followerId, UUID followeeId) {
        jdbc.sql("delete from follows where follower_id = :follower and followee_id = :followee")
                .param("follower", followerId)
                .param("followee", followeeId)
                .update();
    }

    public boolean exists(UUID followerId, UUID followeeId) {
        return jdbc.sql("select exists (select 1 from follows where follower_id = :follower and followee_id = :followee)")
                .param("follower", followerId)
                .param("followee", followeeId)
                .query(Boolean.class).single();
    }

    public long countFollowers(UUID userId) {
        return jdbc.sql("select count(*) from follows where followee_id = :id").param("id", userId)
                .query(Long.class).single();
    }

    public long countFollowing(UUID userId) {
        return jdbc.sql("select count(*) from follows where follower_id = :id").param("id", userId)
                .query(Long.class).single();
    }

    List<UUID> followingIds(UUID userId) {
        return jdbc.sql("select followee_id from follows where follower_id = :id order by created_at desc")
                .param("id", userId)
                .query(UUID.class).list();
    }

    /** People who follow {@code userId}. */
    List<FollowRow> followers(UUID userId, FollowCursor after, int limit) {
        return page("followee_id", "follower_id", userId, after, limit);
    }

    /** People {@code userId} follows. */
    List<FollowRow> following(UUID userId, FollowCursor after, int limit) {
        return page("follower_id", "followee_id", userId, after, limit);
    }

    /**
     * {@code owner} is the column holding userId, {@code other} the column with the people listed.
     * Both are one of two fixed column names from this class, never user input.
     */
    private List<FollowRow> page(String owner, String other, UUID userId, FollowCursor after, int limit) {
        String sql = """
                select u.id, u.username, u.display_name, f.created_at
                from follows f join users u on u.id = f.%2$s
                where f.%1$s = :userId %3$s
                order by f.created_at desc, f.%2$s desc
                limit :limit""".formatted(owner, other,
                after == null ? "" : "and (f.created_at, f.%s) < (:createdAt, :afterId)".formatted(other));
        JdbcClient.StatementSpec statement = jdbc.sql(sql).param("userId", userId).param("limit", limit);
        if (after != null) {
            statement = statement.param("createdAt", Timestamp.from(after.followedAt())).param("afterId", after.userId());
        }
        return statement.query(FollowRepository::row).list();
    }

    private static FollowRow row(ResultSet rs, int rowNum) throws SQLException {
        UserSummary user = new UserSummary(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("display_name"));
        return new FollowRow(user, rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    /** One person in a follow list, with when the follow happened (for the cursor). */
    record FollowRow(UserSummary user, Instant followedAt) {
    }
}
