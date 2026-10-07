package io.github.kerbiy.crate.review.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kerbiy.crate.review.ReviewIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code @Version} without the timing luck of a real race: a transaction reads a review, another
 * writer changes the row underneath it, and the first one's update must be rejected, not applied.
 */
class OptimisticLockingIntegrationTests extends ReviewIntegrationTest {

    @Autowired
    ReviewRepository reviews;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void staleUpdateIsRejectedInsteadOfOverwriting() {
        UUID user = UUID.randomUUID();
        UUID album = UUID.randomUUID();
        put(user, album, 6, null).expectStatus().isCreated();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Review review = reviews.findByUserIdAndAlbumId(user, album).orElseThrow(); // reads version 0
            // Someone else's update, committed in its own transaction: rating 9, version 1.
            otherWriter().executeWithoutResult(other ->
                    jdbc.sql("update reviews set rating = 9, version = version + 1 where user_id = :user")
                            .param("user", user).update());
            review.apply(4, null);
            reviews.flush(); // update ... where id = ? and version = 0 → 0 rows
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The other writer's change survived; the stale one was not applied.
        assertThat(jdbc.sql("select rating from reviews").query(Integer.class).single()).isEqualTo(9);
    }

    /** A separate transaction that commits on its own, like a concurrent request would. */
    private TransactionTemplate otherWriter() {
        TransactionTemplate other = new TransactionTemplate(transaction.getTransactionManager());
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return other;
    }
}
