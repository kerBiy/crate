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

    /**
     * The artist's albums: most releases first; on a tie, earliest first release, then MBID, so the
     * order never depends on the order MusicBrainz happened to return them in. Dates are MusicBrainz's
     * partial ISO strings ("1956", "1956-03", "1956-03-19"), compared as text: a year alone sorts before
     * any full date in that year. Unknown dates go last. MBIDs compare as text too ({@code UUID.compareTo}
     * compares signed numbers, an order nobody would guess).
     */
    private static final Comparator<ReleaseGroup> ARTIST_ORDER =
            Comparator.<ReleaseGroup>comparingInt(SearchRanking::releases).reversed()
            .thenComparing(ReleaseGroup::firstReleaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(album -> album.id().toString());

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
     * The artist's albums first (see {@link #ARTIST_ORDER}); then everything the query found, by
     * {@link #relevance}. Duplicates keep their first (best) position.
     *
     * @param listeners ListenBrainz listener count per album; empty when ListenBrainz couldn't be asked,
     *                  which gives exactly the ranking from before listeners were used (ADR 012)
     */
    static List<ReleaseGroup> merge(List<ReleaseGroup> artistAlbums, List<SearchHit> hits, double popularityWeight,
            double listenerWeight, Map<UUID, Integer> listeners) {
        Stream<ReleaseGroup> artistFirst = artistAlbums.stream().sorted(ARTIST_ORDER);
        // Stable sort: equal values keep MusicBrainz's order.
        Stream<ReleaseGroup> byRelevance = hits.stream()
                .sorted(Comparator.comparingDouble(
                        (SearchHit hit) -> relevance(hit, popularityWeight, listenerWeight, listeners)).reversed())
                .map(SearchHit::releaseGroup);

        Map<UUID, ReleaseGroup> ranked = new LinkedHashMap<>();
        Stream.concat(artistFirst, byRelevance).forEach(album -> ranked.putIfAbsent(album.id(), album));
        return List.copyOf(ranked.values());
    }

    /**
     * MusicBrainz's score + {@code popularityWeight × ln(1 + releases)} + {@code listenerWeight × ln(1 +
     * listeners)}. The logs keep a classic from swamping relevance: 136 vs 1 release is 4.9 vs 0.7, so
     * with a weight of 15 releases are worth up to ~60 score points, not ten thousand; 100 000 listeners
     * with a weight of 2 are worth ~23. Releases say what was pressed (ADR 010), listeners what people
     * play now (ADR 012), which mostly decides short titles such as "am" or "25".
     */
    static double relevance(SearchHit hit, double popularityWeight, double listenerWeight,
            Map<UUID, Integer> listeners) {
        return hit.score()
                + popularityWeight * Math.log1p(releases(hit.releaseGroup()))
                + listenerWeight * Math.log1p(listeners.getOrDefault(hit.releaseGroup().id(), 0));
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
