package io.github.kerbiy.crate.review.review;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

/**
 * Lists are keyset-paginated (SPEC 7), newest first. The "after" queries use a Postgres row
 * comparison, {@code (created_at, id) < (:createdAt, :id)}, which matches the order of
 * reviews_album_idx / reviews_user_idx, so Postgres seeks straight to the cursor instead of
 * reading and skipping rows like OFFSET does.
 */
interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByUserIdAndAlbumId(UUID userId, UUID albumId);

    /** One statement, no load first. Returns the number of rows deleted (0 or 1). */
    @Modifying
    @Query("delete from Review r where r.userId = :userId and r.albumId = :albumId")
    int deleteByUserAndAlbum(UUID userId, UUID albumId);

    @NativeQuery("""
            select * from reviews
            where album_id = :albumId
            order by created_at desc, id desc
            limit :limit""")
    List<Review> findByAlbum(UUID albumId, int limit);

    @NativeQuery("""
            select * from reviews
            where album_id = :albumId and (created_at, id) < (:createdAt, :id)
            order by created_at desc, id desc
            limit :limit""")
    List<Review> findByAlbumAfter(UUID albumId, Instant createdAt, UUID id, int limit);

    @NativeQuery("""
            select * from reviews
            where user_id = :userId
            order by created_at desc, id desc
            limit :limit""")
    List<Review> findByUser(UUID userId, int limit);

    @NativeQuery("""
            select * from reviews
            where user_id = :userId and (created_at, id) < (:createdAt, :id)
            order by created_at desc, id desc
            limit :limit""")
    List<Review> findByUserAfter(UUID userId, Instant createdAt, UUID id, int limit);
}
