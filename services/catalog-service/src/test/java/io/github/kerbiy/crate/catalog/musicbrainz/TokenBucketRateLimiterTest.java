package io.github.kerbiy.crate.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    private static final Duration SECOND = Duration.ofSeconds(1);

    /** A clock that only moves when someone sleeps (or the test advances it): no real waiting. */
    static final class FakeTime implements Sleeper {
        long now = 123_456_789L; // arbitrary start: nanoTime has no meaningful zero
        final List<Duration> sleeps = new ArrayList<>();

        long nanoTime() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            sleeps.add(duration);
            now += duration.toNanos();
        }

        void advance(Duration duration) {
            now += duration.toNanos();
        }
    }

    private final FakeTime time = new FakeTime();
    private final TokenBucketRateLimiter limiter =
            new TokenBucketRateLimiter(SECOND, Duration.ofSeconds(10), time::nanoTime, time);

    @Test
    void firstPermitIsImmediate() {
        limiter.acquire();

        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void backToBackRequestsAreSpacedOneIntervalApart() {
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        assertThat(time.sleeps).containsExactly(SECOND, SECOND);
    }

    @Test
    void waitsOnlyForTheRestOfTheInterval() {
        limiter.acquire();
        time.advance(Duration.ofMillis(300));
        limiter.acquire();

        assertThat(time.sleeps).containsExactly(Duration.ofMillis(700));
    }

    @Test
    void idleTimeDoesNotBuildUpABurst() {
        limiter.acquire();
        time.advance(Duration.ofSeconds(5));
        limiter.acquire(); // free: the bucket refilled...
        limiter.acquire(); // ...but holds a single token, so this one waits

        assertThat(time.sleeps).containsExactly(SECOND);
    }

    @Test
    void reservationsQueueUpWhenCallersDontSleep() {
        // A sleeper that returns instantly and doesn't move the clock: like 4 threads arriving at once.
        List<Duration> waits = new ArrayList<>();
        TokenBucketRateLimiter queue = new TokenBucketRateLimiter(SECOND, Duration.ofSeconds(10),
                time::nanoTime, waits::add);

        for (int i = 0; i < 4; i++) {
            queue.acquire();
        }

        assertThat(waits).containsExactly(SECOND, Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @Test
    void refusesWhenTheWaitWouldExceedMaxWaitAndKeepsTheSlotFree() {
        TokenBucketRateLimiter impatient = new TokenBucketRateLimiter(SECOND, Duration.ofMillis(1500),
                time::nanoTime, duration -> { });
        impatient.acquire(); // slot at 0
        impatient.acquire(); // slot at 1 s
        assertThatThrownBy(impatient::acquire).isInstanceOf(MusicBrainzException.class); // would be 2 s

        time.advance(Duration.ofMillis(600));
        impatient.acquire(); // the refused call didn't take 2 s, so this gets it (1.4 s wait)
    }

    @Test
    void concurrentCallersAreSpacedWithRealTime() throws Exception {
        Duration interval = Duration.ofMillis(20);
        TokenBucketRateLimiter real = TokenBucketRateLimiter.realTime(interval, Duration.ofSeconds(1));
        List<Long> passedAt = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);

        long startedAt;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                pool.submit(() -> {
                    start.await();
                    real.acquire();
                    passedAt.add(System.nanoTime());
                    return null;
                });
            }
            startedAt = System.nanoTime();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        // 5 permits = slots at t, t+20, t+40, t+60, t+80 ms. Sleep never returns early, so the last
        // caller can't get through before 4 intervals; without the lock they'd all pass at once.
        assertThat(passedAt).hasSize(5);
        long lastPassed = passedAt.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(Duration.ofNanos(lastPassed - startedAt)).isGreaterThanOrEqualTo(interval.multipliedBy(4));
    }
}
