package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks one link per platform for the album page (docs/adr/015-where-to-listen.md). MusicBrainz keeps
 * streaming links on releases, not on the release group, and a popular album has them on many digital
 * releases (deluxe editions, regional ones). So, per platform:
 *
 * <ol>
 *   <li>the link on the release the tracklist was read from ({@link ReleaseChoice}), if it has one;</li>
 *   <li>otherwise one on a release with the same track count: the same edition, most likely a digital
 *       copy of it;</li>
 *   <li>otherwise one on any release, so a deluxe-only link still beats no link;</li>
 * </ol>
 *
 * each time the earliest release first, then by MBID, so the same releases always give the same links.
 */
public final class ListenLinkChoice {

    private ListenLinkChoice() {
    }

    private static final Comparator<ReleaseSummary> EARLIEST = Comparator
            .comparing((ReleaseSummary release) -> ReleaseChoice.sortableDate(release.date()))
            .thenComparing(release -> release.id().toString());

    /**
     * @param chosen the release the tracklist comes from; null when there is none
     * @return at most one link per platform, in {@link ListenPlatform} order
     */
    public static List<ListenLink> choose(List<ReleaseSummary> releases, ReleaseSummary chosen) {
        Comparator<ReleaseSummary> preference = Comparator
                .comparing((ReleaseSummary release) -> !release.equals(chosen))
                .thenComparing(release -> chosen == null || release.trackCount() != chosen.trackCount())
                .thenComparing(EARLIEST);
        List<ReleaseSummary> ordered = releases.stream().sorted(preference).toList();
        return Arrays.stream(ListenPlatform.values())
                .map(platform -> first(ordered, platform))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ListenLink> first(List<ReleaseSummary> ordered, ListenPlatform platform) {
        return ordered.stream()
                .flatMap(release -> release.links().stream())
                .filter(link -> link.platform() == platform)
                .findFirst();
    }
}
