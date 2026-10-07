package io.github.kerbiy.crate.catalog.coverart;

import java.util.UUID;

/** Whether a release group has a front cover. {@link CoverArtClient} in production, a fake in tests. */
@FunctionalInterface
public interface CoverSource {

    enum Cover { PRESENT, ABSENT, UNKNOWN }

    Cover check(UUID releaseGroupId);
}
