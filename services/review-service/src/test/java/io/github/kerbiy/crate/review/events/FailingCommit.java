package io.github.kerbiy.crate.review.events;

import io.github.kerbiy.crate.contracts.review.ReviewEvent;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Test-only: makes the real write path roll back, for one user. A BEFORE_COMMIT listener runs
 * inside the transaction, just before the commit, so throwing here rolls the whole thing back,
 * after ReviewService has already published its event.
 */
@TestComponent
public class FailingCommit {

    /** Any change by this user fails at commit time. */
    public static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void beforeCommit(ReviewEvent event) {
        if (USER.equals(event.userId())) {
            throw new IllegalStateException("Simulated failure before commit");
        }
    }
}
