package io.github.kerbiy.crate.contracts.review;

/** The review.events topic (SPEC 6.2): key = albumId, so one album's events stay in order. */
public final class ReviewEvents {

    public static final String TOPIC = "review.events";

    /** Enough to show consumer-group parallelism on one broker. Fixed: changing it remaps keys. */
    public static final int PARTITIONS = 3;

    public static final String CREATED = "ReviewCreated";
    public static final String UPDATED = "ReviewUpdated";
    public static final String DELETED = "ReviewDeleted";

    private ReviewEvents() {
    }
}
