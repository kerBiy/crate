package io.github.kerbiy.crate.catalog.coverart;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Checks many covers at once, politely.
 *
 * <ul>
 *   <li>One virtual thread per album: each check mostly waits on the network, and a waiting virtual
 *       thread costs almost nothing, so plain blocking code is fine.</li>
 *   <li>A semaphore with {@code concurrency} permits: a thread takes one before calling the Cover Art
 *       Archive and returns it after, so no more than that many requests are ever in flight.</li>
 *   <li>A deadline for the whole batch: whatever hasn't answered by then is abandoned and left
 *       unknown. A search must not hang on a slow archive.</li>
 * </ul>
 */
public class CoverChecker {

    private final CoverSource covers;
    private final int concurrency;
    private final Duration deadline;

    public CoverChecker(CoverSource covers, int concurrency, Duration deadline) {
        this.covers = covers;
        this.concurrency = concurrency;
        this.deadline = deadline;
    }

    /** true = has a cover, false = confirmed none. Albums without a clear answer are left out. */
    public Map<UUID, Boolean> check(Collection<UUID> releaseGroupIds) {
        Semaphore permits = new Semaphore(concurrency);
        Map<UUID, Boolean> answers = new ConcurrentHashMap<>();
        long stopAt = System.nanoTime() + deadline.toNanos();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> checks = releaseGroupIds.stream().distinct()
                    .<Future<?>>map(id -> threads.submit(() -> {
                        permits.acquire();
                        try {
                            switch (covers.check(id)) {
                                case PRESENT -> answers.put(id, true);
                                case ABSENT -> answers.put(id, false);
                                case UNKNOWN -> { }
                            }
                        } finally {
                            permits.release();
                        }
                        return null;
                    }))
                    .toList();
            for (Future<?> check : checks) {
                if (!awaitUntil(check, stopAt)) {
                    // Interrupts the stragglers: waiting ones give up their place, running ones their request.
                    threads.shutdownNow();
                    break;
                }
            }
            // Taken now, not after the executor has closed: an answer arriving past the deadline is ignored.
            return Map.copyOf(answers);
        }
    }

    /** False once the deadline has passed. A check that failed still counts as finished. */
    private static boolean awaitUntil(Future<?> check, long stopAt) {
        try {
            check.get(Math.max(0, stopAt - System.nanoTime()), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
