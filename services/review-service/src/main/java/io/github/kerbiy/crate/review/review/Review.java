package io.github.kerbiy.crate.review.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** One user's rating of one album, with optional text. Unique per (user, album). */
@Entity
@Table(name = "reviews")
public class Review {

    // Hibernate generates a random UUID before the insert, so save() knows the entity is new.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private UUID albumId;

    /** Half stars: 1 = ½ star, 10 = 5 stars. A smallint column, so a short here. */
    private short rating;

    @Column(columnDefinition = "text")
    private String body;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Optimistic locking. Every update runs {@code ... where id = ? and version = ?} and bumps it;
     * if another transaction changed the row first, 0 rows match and Hibernate throws instead of
     * silently overwriting that change.
     */
    @Version
    private Long version;

    protected Review() {
        // for JPA
    }

    Review(UUID userId, UUID albumId, int rating, String body) {
        this.userId = userId;
        this.albumId = albumId;
        this.rating = (short) rating;
        this.body = body;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Replaces rating and text. Returns false, and touches nothing, when both are already equal:
     * then Hibernate sees no change and doesn't write (or bump the version) at all.
     */
    boolean apply(int newRating, String newBody) {
        if (rating == newRating && Objects.equals(body, newBody)) {
            return false;
        }
        rating = (short) newRating;
        body = newBody;
        updatedAt = now();
        return true;
    }

    // Postgres keeps microseconds; Instant.now() can have more. Truncating here means the value we
    // return (and put in a pagination cursor) is exactly the stored one.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAlbumId() {
        return albumId;
    }

    public int getRating() {
        return rating;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
