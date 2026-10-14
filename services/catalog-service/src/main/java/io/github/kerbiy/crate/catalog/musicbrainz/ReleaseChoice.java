package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the one release of a release group that the album page reads its length, label and tracklist
 * from (docs/adr/013-release-for-album-details.md): the standard edition, from the album's usual label,
 * first out.
 *
 * <ol>
 *   <li>Only releases with tracks.</li>
 *   <li>The most common track count: the standard edition. Bonus-track and deluxe editions are rarer.
 *       On a tie the smaller count, since bonus tracks only ever add.</li>
 *   <li>The most common label among those, so a lone import (OK Computer's Japanese EMI CD, out three
 *       weeks before the European Parlophone one) doesn't stand for the album. On a tie, the label of
 *       the earliest release. Skipped when no release names a label.</li>
 *   <li>The earliest date. A partial date ("1997") sorts after the full dates it contains; unknown last.</li>
 *   <li>The MBID, so the same releases always give the same choice.</li>
 * </ol>
 */
public final class ReleaseChoice {

    private ReleaseChoice() {
    }

    private static final Comparator<ReleaseSummary> EARLIEST = Comparator
            .comparing((ReleaseSummary release) -> sortableDate(release.date()))
            .thenComparing(release -> release.id().toString());

    /** Empty when no release has any tracks. */
    public static Optional<ReleaseSummary> choose(List<ReleaseSummary> releases) {
        List<ReleaseSummary> withTracks = releases.stream().filter(release -> release.trackCount() > 0).toList();
        if (withTracks.isEmpty()) {
            return Optional.empty();
        }
        List<ReleaseSummary> edition = standardEdition(withTracks);
        return usualLabel(edition).stream().min(EARLIEST);
    }

    private static List<ReleaseSummary> standardEdition(List<ReleaseSummary> releases) {
        Map<Integer, Long> releasesPerCount = releases.stream()
                .collect(Collectors.groupingBy(ReleaseSummary::trackCount, Collectors.counting()));
        long most = releasesPerCount.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        int standard = releasesPerCount.entrySet().stream()
                .filter(entry -> entry.getValue() == most)
                .mapToInt(Map.Entry::getKey)
                .min()
                .orElseThrow();
        return releases.stream().filter(release -> release.trackCount() == standard).toList();
    }

    private static List<ReleaseSummary> usualLabel(List<ReleaseSummary> releases) {
        Map<String, Long> releasesPerLabel = releases.stream()
                .flatMap(release -> release.labels().stream().distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (releasesPerLabel.isEmpty()) {
            return releases;
        }
        long most = releasesPerLabel.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        Set<String> tied = releasesPerLabel.entrySet().stream()
                .filter(entry -> entry.getValue() == most)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        String label = releases.stream()
                .sorted(EARLIEST)
                .flatMap(release -> release.labels().stream())
                .filter(tied::contains)
                .findFirst()
                .orElseThrow();
        return releases.stream().filter(release -> release.labels().contains(label)).toList();
    }

    /** "1997-05-21" stays; "1997-05" → "1997-05-99"; "1997" → "1997-99-99"; null → after everything. */
    static String sortableDate(String date) {
        if (date == null || date.isBlank()) {
            return "9999-99-99";
        }
        int parts = date.split("-").length;
        return date + "-99".repeat(Math.max(0, 3 - parts));
    }
}
