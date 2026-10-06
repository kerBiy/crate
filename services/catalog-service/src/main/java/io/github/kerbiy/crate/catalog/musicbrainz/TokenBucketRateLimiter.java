package io.github.kerbiy.crate.catalog.musicbrainz;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Token bucket with capacity 1: at most one permit per {@code interval}, no bursts.
 *
 * <p>Nothing refills the bucket in the background. We only remember when the next permit is free
 * ({@code nextFreeAt}). A caller reserves that slot under the lock, moves {@code nextFreeAt} one
 * interval further, and then sleeps <em>outside</em> the lock until its slot arrives. So concurrent
 * callers line up at t, t+1s, t+2s... without anyone holding the lock while sleeping.
 *
 * <p>Process-local: correct only while a single catalog-service instance talks to MusicBrainz.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final long intervalNanos;
    private final long maxWaitNanos;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;

    private final Object lock = new Object();
    private long nextFreeAt; // guarded by lock
    private boolean used; // guarded by lock; the very first permit is free whatever the clock says

    public TokenBucketRateLimiter(Duration interval, Duration maxWait, LongSupplier nanoClock, Sleeper sleeper) {
        this.intervalNanos = interval.toNanos();
        this.maxWaitNanos = maxWait.toNanos();
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    public static TokenBucketRateLimiter realTime(Duration interval, Duration maxWait) {
        return new TokenBucketRateLimiter(interval, maxWait, System::nanoTime, Sleeper.REAL);
    }

    /**
     * Blocks until the caller may send one request.
     *
     * @throws MusicBrainzException if the wait would exceed {@code maxWait} (no slot is taken then)
     */
    @Override
    public void acquire() {
        long waitNanos;
        synchronized (lock) {
            long now = nanoClock.getAsLong();
            // nanoTime values may be negative or wrap, so compare differences, never raw values.
            long slot = !used || now - nextFreeAt > 0 ? now : nextFreeAt;
            waitNanos = slot - now;
            if (waitNanos > maxWaitNanos) {
                throw new MusicBrainzException("MusicBrainz rate limit queue is full");
            }
            nextFreeAt = slot + intervalNanos;
            used = true;
        }
        if (waitNanos > 0) {
            try {
                sleeper.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MusicBrainzException("Interrupted while waiting for the MusicBrainz rate limit", e);
            }
        }
    }
}
