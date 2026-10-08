package io.github.kerbiy.crate.catalog.stats;

import io.github.kerbiy.crate.contracts.review.ReviewCreated;
import io.github.kerbiy.crate.contracts.review.ReviewDeleted;
import io.github.kerbiy.crate.contracts.review.ReviewEvent;
import io.github.kerbiy.crate.contracts.review.ReviewUpdated;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns review events into album_stats changes (SPEC 5.3).
 *
 * <p><b>Idempotent:</b> Kafka delivers at least once, so the same event can arrive twice. The event
 * id goes into processed_events in the same transaction as the stats change: both commit, or neither
 * does. A second delivery finds the id already there and changes nothing.
 *
 * <p><b>Deltas on the distribution:</b> each event adds to or subtracts from one or two half-star
 * buckets; count and sum follow from the buckets. A bucket never goes below zero: removing a rating
 * catalog never counted (an event lost after commit, ADR-006, or a rating older than the events) is
 * clamped at 0 and logged, instead of leaving a negative count that would skew every later average.
 */
@Service
class AlbumStatsService {

    private static final Logger log = LoggerFactory.getLogger(AlbumStatsService.class);

    private final AlbumStatsRepository stats;

    AlbumStatsService(AlbumStatsRepository stats) {
        this.stats = stats;
    }

    @Transactional
    void apply(UUID eventId, ReviewEvent event) {
        if (!stats.markProcessed(eventId)) {
            log.debug("Event {} already processed, skipping", eventId);
            return;
        }
        UUID album = event.albumId();
        // A text-only edit has rating == oldRating: nothing to change in the numbers.
        if (event instanceof ReviewUpdated updated && rating(updated.rating()) == rating(updated.oldRating())) {
            return;
        }
        int[] buckets = stats.lockDistribution(album);
        switch (event) {
            case ReviewCreated created -> shift(buckets, created.rating(), 1, eventId, album);
            case ReviewUpdated updated -> {
                shift(buckets, updated.oldRating(), -1, eventId, album);
                shift(buckets, updated.rating(), 1, eventId, album);
            }
            case ReviewDeleted deleted -> shift(buckets, deleted.rating(), -1, eventId, album);
        }
        stats.save(album, buckets);
    }

    private static void shift(int[] buckets, int rating, int delta, UUID eventId, UUID album) {
        int i = rating(rating) - 1;
        int changed = buckets[i] + delta;
        if (changed < 0) {
            log.warn("Event {} would make album {}'s count of rating {} negative ({}); clamped at 0. "
                    + "Its stats had already drifted (a lost event, see ADR-006).", eventId, album, rating, changed);
            changed = 0;
        }
        buckets[i] = changed;
    }

    /** A malformed event must fail loudly, not write into a wrong bucket. */
    private static int rating(int rating) {
        if (rating < 1 || rating > 10) {
            throw new IllegalArgumentException("Rating out of range 1-10: " + rating);
        }
        return rating;
    }
}
