package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.musicbrainz.ArtistCredit;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.musicbrainz.SearchHit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** How MusicBrainz's answers become one ranked list (docs/adr/010-search-ranking.md). No I/O. */
final class SearchRanking {

    private SearchRanking() {
    }

    /**
     * The artist the whole query names, if any result is credited to them: "radiohead", "the beatles",
     * "beatles", "bjork". Only a whole-query match counts: for "radiohead kid a" the general query
     * already finds Kid A, and putting the artist's other albums first would bury it.
     */
    static Optional<UUID> matchingArtist(String normalizedQuery, List<SearchHit> hits) {
        String wanted = withoutArticle(normalizedQuery);
        return hits.stream()
                .flatMap(hit -> hit.releaseGroup().artists().stream()
                        .filter(artist -> artist.id() != null)
                        .filter(artist -> names(artist, hit.releaseGroup()).anyMatch(wanted::equals)))
                .map(ArtistCredit::id)
                .findFirst();
    }

    /**
     * The artist's albums first, most released first; then everything the query found, by
     * MusicBrainz's score plus {@code popularityWeight × ln(1 + releases)}. The log keeps a
     * 136-release classic from swamping relevance: 136 vs 1 release is 4.9 vs 0.7, so with a weight
     * of 15 popularity is worth up to ~60 score points, not ten thousand. Duplicates keep their
     * first (best) position.
     */
    static List<ReleaseGroup> merge(List<ReleaseGroup> artistAlbums, List<SearchHit> hits, double popularityWeight) {
        Stream<ReleaseGroup> artistFirst = artistAlbums.stream()
                .sorted(Comparator.comparingInt(SearchRanking::releases).reversed());
        // Stable sort: equal values keep MusicBrainz's order.
        Stream<ReleaseGroup> byRelevance = hits.stream()
                .sorted(Comparator.comparingDouble((SearchHit hit) -> relevance(hit, popularityWeight)).reversed())
                .map(SearchHit::releaseGroup);

        Map<UUID, ReleaseGroup> ranked = new LinkedHashMap<>();
        Stream.concat(artistFirst, byRelevance).forEach(album -> ranked.putIfAbsent(album.id(), album));
        return List.copyOf(ranked.values());
    }

    static double relevance(SearchHit hit, double popularityWeight) {
        return hit.score() + popularityWeight * Math.log1p(releases(hit.releaseGroup()));
    }

    private static int releases(ReleaseGroup album) {
        return album.releaseCount() == null ? 0 : album.releaseCount();
    }

    /** The artist's own name, and the name as credited on this album when it's the only artist. */
    private static Stream<String> names(ArtistCredit artist, ReleaseGroup album) {
        Stream<String> credited = album.artists().size() == 1 ? Stream.of(album.artistCredit()) : Stream.empty();
        return Stream.concat(Stream.of(artist.name()), credited)
                .map(SearchText::normalize)
                .map(SearchRanking::withoutArticle);
    }

    private static String withoutArticle(String normalized) {
        return normalized.startsWith("the ") ? normalized.substring(4) : normalized;
    }
}
