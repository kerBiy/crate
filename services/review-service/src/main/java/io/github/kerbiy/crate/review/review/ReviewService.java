package io.github.kerbiy.crate.review.review;

import io.github.kerbiy.crate.contracts.review.ReviewCreated;
import io.github.kerbiy.crate.contracts.review.ReviewDeleted;
import io.github.kerbiy.crate.contracts.review.ReviewUpdated;
import io.github.kerbiy.crate.review.catalog.CatalogClient;
import io.github.kerbiy.crate.review.users.UserClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** Total tries per upsert. A double-click needs 2: the loser of the insert race becomes an update. */
    static final int MAX_ATTEMPTS = 3;

    private static final String USER_ALBUM_KEY = "reviews_user_album_key";

    private final ReviewRepository reviews;
    private final CatalogClient catalog;
    private final UserClient users;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    ReviewService(ReviewRepository reviews, CatalogClient catalog, UserClient users, TransactionTemplate transaction,
            ApplicationEventPublisher events) {
        this.reviews = reviews;
        this.catalog = catalog;
        this.users = users;
        this.transaction = transaction;
        this.events = events;
    }

    record Upserted(Review review, boolean created) {
    }

    /**
     * Creates or replaces this user's review of the album.
     *
     * <p>Two concurrent requests (a double-click) can collide in two ways:
     * <ul>
     *   <li>both see "no review" and insert: the unique (user_id, album_id) constraint rejects one;
     *   <li>both update the same row: {@code @Version} rejects the one that read the older version.
     * </ul>
     * Either way the loser retries from a fresh read, so it becomes an update of the winner's row.
     * Each attempt is its own transaction: after a failed statement Postgres aborts the
     * transaction, so the retry can't happen inside it.
     *
     * <p>A change publishes ReviewCreated or ReviewUpdated, which ReviewEventPublisher sends to Kafka
     * only after the attempt's transaction commits; a rolled-back attempt's event is dropped.
     */
    Upserted upsert(UUID userId, UUID albumId, int rating, String body) {
        // An HTTP call: never inside a DB transaction (it would hold a connection while waiting).
        catalog.requireAlbum(albumId);

        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> saveOnce(userId, albumId, rating, body));
            } catch (DataIntegrityViolationException e) {
                if (!violates(e, USER_ALBUM_KEY)) {
                    throw e;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw new ReviewConflictException(e);
                }
                log.debug("Concurrent create of review user={} album={}, retrying as update", userId, albumId);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ReviewConflictException(e);
                }
                log.debug("Concurrent update of review user={} album={}, retrying", userId, albumId);
            }
        }
    }

    private Upserted saveOnce(UUID userId, UUID albumId, int rating, String body) {
        Optional<Review> existing = reviews.findByUserIdAndAlbumId(userId, albumId);
        if (existing.isPresent()) {
            Review review = existing.get();
            // Read before apply(). @Version guarantees nobody changed the row since this read, so
            // oldRating really is the rating this update replaces.
            int oldRating = review.getRating();
            if (review.apply(rating, body)) {
                // Flush now, so a version conflict is thrown here, inside the attempt, not at commit.
                reviews.flush();
                events.publishEvent(new ReviewUpdated(review.getId(), userId, albumId, review.getRating(), oldRating,
                        review.getBody() != null));
            }
            return new Upserted(review, false);
        }
        // saveAndFlush: the insert runs now, so a unique violation surfaces inside this attempt.
        Review created = reviews.saveAndFlush(new Review(userId, albumId, rating, body));
        events.publishEvent(new ReviewCreated(created.getId(), userId, albumId, created.getRating(),
                created.getBody() != null));
        return new Upserted(created, true);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraint.equals(violation.getConstraintName());
            }
        }
        return false;
    }

    Optional<Review> find(UUID userId, UUID albumId) {
        return reviews.findByUserIdAndAlbumId(userId, albumId);
    }

    /**
     * Idempotent: deleting a review that isn't there is fine, and publishes nothing.
     *
     * <p>The row is read with {@code select ... for update}, which locks it until this transaction
     * ends. Two concurrent deletes (a double-click) then can't both see the row: the second waits,
     * and once the first commits it finds nothing. So exactly one ReviewDeleted goes out, and
     * catalog subtracts the rating once. It carries the rating, which catalog needs to subtract.
     */
    @Transactional
    void delete(UUID userId, UUID albumId) {
        reviews.findForUpdate(userId, albumId).ifPresent(review -> {
            reviews.delete(review);
            reviews.flush();
            events.publishEvent(new ReviewDeleted(review.getId(), userId, albumId, review.getRating()));
        });
    }

    ReviewPage ofAlbum(UUID albumId, ReviewCursor cursor, int limit) {
        // One extra row tells us whether there's a next page, without a count query.
        List<Review> rows = cursor == null
                ? reviews.findByAlbum(albumId, limit + 1)
                : reviews.findByAlbumAfter(albumId, cursor.createdAt(), cursor.id(), limit + 1);
        return page(rows, limit);
    }

    ReviewPage ofUser(UUID userId, ReviewCursor cursor, int limit) {
        List<Review> rows = cursor == null
                ? reviews.findByUser(userId, limit + 1)
                : reviews.findByUserAfter(userId, cursor.createdAt(), cursor.id(), limit + 1);
        return page(rows, limit);
    }

    /**
     * Feed v1, fan-out on read (SPEC 3.6, ADR-007): ask user-service who I follow, then read their
     * reviews here. The HTTP call happens before any query, so no DB connection waits on it.
     *
     * @throws io.github.kerbiy.crate.review.users.UserServiceUnavailableException user-service couldn't answer
     */
    ReviewPage feed(UUID userId, ReviewCursor cursor, int limit) {
        List<UUID> following = users.followingIds(userId);
        if (following.isEmpty()) {
            return new ReviewPage(List.of(), null);
        }
        UUID[] ids = following.toArray(UUID[]::new);
        List<Review> rows = cursor == null
                ? reviews.findFeed(ids, limit + 1)
                : reviews.findFeedAfter(ids, cursor.createdAt(), cursor.id(), limit + 1);
        return page(rows, limit);
    }

    private static ReviewPage page(List<Review> rows, int limit) {
        boolean more = rows.size() > limit;
        List<Review> items = more ? rows.subList(0, limit) : rows;
        String next = more ? ReviewCursor.of(items.getLast()).encode() : null;
        return new ReviewPage(items.stream().map(ReviewResponse::from).toList(), next);
    }
}
