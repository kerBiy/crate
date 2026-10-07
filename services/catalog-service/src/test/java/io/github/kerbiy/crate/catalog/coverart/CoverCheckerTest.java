package io.github.kerbiy.crate.catalog.coverart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kerbiy.crate.catalog.coverart.CoverSource.Cover;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CoverCheckerTest {

    private static final Duration GENEROUS = Duration.ofSeconds(10);

    @Test
    void mapsAnswersAndLeavesUnknownOut() {
        UUID present = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        Map<UUID, Cover> archive = Map.of(present, Cover.PRESENT, absent, Cover.ABSENT, unknown, Cover.UNKNOWN);

        Map<UUID, Boolean> answers = new CoverChecker(archive::get, 8, GENEROUS).check(List.of(present, absent, unknown));

        assertThat(answers).containsExactlyInAnyOrderEntriesOf(Map.of(present, true, absent, false));
    }

    @Test
    void neverMoreThanTheLimitInFlightButSeveralAtOnce() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger mostInFlight = new AtomicInteger();
        CoverSource slowArchive = id -> {
            mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sleep(Duration.ofMillis(50));
            inFlight.decrementAndGet();
            return Cover.PRESENT;
        };

        Map<UUID, Boolean> answers = new CoverChecker(slowArchive, 4, GENEROUS).check(ids(20));

        assertThat(answers).hasSize(20);
        assertThat(mostInFlight).hasValue(4);
    }

    @Test
    void deadlineAbandonsWhatIsStillRunning() {
        UUID quick = UUID.randomUUID();
        UUID stuck = UUID.randomUUID();
        CoverSource archive = id -> {
            if (id.equals(stuck)) {
                sleep(Duration.ofSeconds(30));
            }
            return Cover.PRESENT;
        };

        long start = System.nanoTime();
        Map<UUID, Boolean> answers = new CoverChecker(archive, 8, Duration.ofMillis(200)).check(List.of(quick, stuck));

        assertThat(answers).containsOnlyKeys(quick);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void duplicatesAreCheckedOnce() {
        AtomicInteger calls = new AtomicInteger();
        UUID id = UUID.randomUUID();

        new CoverChecker(any -> {
            calls.incrementAndGet();
            return Cover.ABSENT;
        }, 8, GENEROUS).check(List.of(id, id, id));

        assertThat(calls).hasValue(1);
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    /** Like a blocking HTTP call: gives up when the checker interrupts it. */
    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
