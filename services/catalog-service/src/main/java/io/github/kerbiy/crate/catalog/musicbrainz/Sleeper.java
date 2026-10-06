package io.github.kerbiy.crate.catalog.musicbrainz;

import java.time.Duration;

/** Blocks the current thread. Tests pass a fake that records the duration instead of waiting. */
@FunctionalInterface
public interface Sleeper {

    Sleeper REAL = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
