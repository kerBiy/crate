package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.album.AlbumRepository;
import io.github.kerbiy.crate.catalog.album.AlbumRow;
import io.github.kerbiy.crate.catalog.album.AlbumSummary;
import io.github.kerbiy.crate.catalog.coverart.CoverArtProperties;
import io.github.kerbiy.crate.catalog.coverart.CoverChecker;
import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzClient;
import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzException;
import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzProperties;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.musicbrainz.SearchHit;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Album search as a read-through cache (SPEC 5.3, ADR 010). MusicBrainz finds and scores, ListenBrainz
 * listener counts lift what people actually play (ADR 012); we store the ranked answer per query and
 * replay it until the cache entry expires. Albums the Cover Art Archive has no cover for are left out.
 *
 * <p>No {@code @Transactional} here on purpose: the MusicBrainz and ListenBrainz calls can take seconds and must not
 * hold a DB transaction (and connection) open. Each repository call is its own short transaction.
 */
@Service
class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final AlbumRepository albums;
    private final SearchCacheRepository searchCache;
    private final MusicBrainzClient musicBrainz;
    private final ListenBrainzClient listenBrainz;
    private final CoverChecker covers;
    private final SearchProperties properties;
    private final CoverArtProperties coverArt;
    private final ListenBrainzProperties listenBrainzProperties;

    SearchService(AlbumRepository albums, SearchCacheRepository searchCache, MusicBrainzClient musicBrainz,
            ListenBrainzClient listenBrainz, CoverChecker covers, SearchProperties properties,
            CoverArtProperties coverArt, ListenBrainzProperties listenBrainzProperties) {
        this.albums = albums;
        this.searchCache = searchCache;
        this.musicBrainz = musicBrainz;
        this.listenBrainz = listenBrainz;
        this.covers = covers;
        this.properties = properties;
        this.coverArt = coverArt;
        this.listenBrainzProperties = listenBrainzProperties;
    }

    SearchResponse search(String query, int limit) {
        // 1. Same normalization as albums.search_text, so "Björk" and "bjork" share a cache entry.
        String normalized = SearchText.normalize(query);
        if (normalized.isEmpty()) {
            throw Problems.invalidField("q", "must contain letters or digits");
        }

        // 2. MusicBrainz answered this query recently: replay its ranking.
        if (searchCache.isFresh(normalized, properties.cacheTtl(), properties.cacheTtlWithoutListeners())) {
            return response(albums.findRanked(normalized, limit), false);
        }

        // 3. Ask MusicBrainz (outside any transaction).
        Found found;
        try {
            found = askMusicBrainz(query, normalized);
        } catch (MusicBrainzException e) {
            // 4. Never fail a search because MusicBrainz is down: serve what we have, flagged.
            log.warn("MusicBrainz search for '{}' failed, returning local results: {}", normalized, e.getMessage());
            return response(albums.search(normalized, properties.matchThreshold(), limit), true);
        }

        // 5. Rank with listener counts, store the albums, their counts and the ranking.
        List<UUID> hitIds = found.hits().stream().map(hit -> hit.releaseGroup().id()).toList();
        Listeners listeners = listenersFor(hitIds, normalized);
        List<ReleaseGroup> ranked = SearchRanking.merge(found.artistAlbums(), found.hits(),
                properties.popularityWeight(), properties.listenerWeight(), listeners.all());
        albums.upsertAll(ranked);
        albums.recordListeners(listeners.fetched());
        checkCovers(ranked);
        // Recorded even when MusicBrainz found nothing, so a nonsense query doesn't hit it every time.
        // Ranked without listener counts (ListenBrainz failed): kept for a day, not a week.
        searchCache.store(normalized, ranked.stream().map(ReleaseGroup::id).toList(), listeners.failed());
        return response(albums.findRanked(normalized, limit), false);
    }

    /**
     * One to three MusicBrainz requests: the query itself; the same query allowing misspellings, only
     * if the first found nothing; and the artist's albums, if the query is an artist's name.
     */
    private Found askMusicBrainz(String query, String normalized) {
        int limit = properties.musicBrainzLimit();
        List<SearchHit> hits = musicBrainz.searchReleaseGroups(query, false, limit);
        if (hits.isEmpty()) {
            hits = musicBrainz.searchReleaseGroups(query, true, limit);
        }
        List<ReleaseGroup> artistAlbums = SearchRanking.matchingArtist(normalized, hits)
                .map(artist -> musicBrainz.artistAlbums(artist, limit))
                .orElse(List.of());
        return new Found(hits, artistAlbums);
    }

    /**
     * Listener counts for the query's results: the ones stored in the last 30 days, plus one
     * ListenBrainz call for the rest (none if nothing is missing). The artist's own albums don't need
     * any: they are ordered by releases. If ListenBrainz fails, no counts at all, not even stored ones,
     * so the ranking is exactly what it was before listeners were used.
     */
    private Listeners listenersFor(List<UUID> hitIds, String normalized) {
        Map<UUID, Integer> stored = albums.listenerCounts(hitIds, listenBrainzProperties.refreshAfter());
        List<UUID> missing = hitIds.stream().filter(id -> !stored.containsKey(id)).distinct().toList();
        if (missing.isEmpty()) {
            return new Listeners(stored, Map.of(), false);
        }
        try {
            long start = System.nanoTime();
            Map<UUID, Integer> fetched = listenBrainz.listeners(missing);
            log.debug("Fetched {} listener counts in {} ms", fetched.size(), (System.nanoTime() - start) / 1_000_000);
            Map<UUID, Integer> all = new HashMap<>(stored);
            all.putAll(fetched);
            return new Listeners(all, fetched, false);
        } catch (ListenBrainzException e) {
            log.warn("ListenBrainz failed for '{}', ranking without listener counts: {}", normalized, e.getMessage());
            return new Listeners(Map.of(), Map.of(), true);
        }
    }

    /**
     * Asks the Cover Art Archive about the top of the ranking, so albums without a cover can be left
     * out. Only albums not checked before (or "no cover" a month ago): a warm search never gets here,
     * and an artist searched again costs nothing. Runs outside any transaction, like MusicBrainz.
     */
    private void checkCovers(List<ReleaseGroup> ranked) {
        List<UUID> top = ranked.stream().limit(properties.coverCheckCount()).map(ReleaseGroup::id).toList();
        List<UUID> unchecked = albums.needingCoverCheck(top, coverArt.recheckAfter());
        if (!unchecked.isEmpty()) {
            long start = System.nanoTime();
            Map<UUID, Boolean> answers = covers.check(unchecked);
            log.debug("Checked {} covers in {} ms, {} answered", unchecked.size(),
                    (System.nanoTime() - start) / 1_000_000, answers.size());
            albums.recordCovers(answers);
        }
    }

    /** What MusicBrainz answered: the query's results, and the artist's albums if the query names one. */
    private record Found(List<SearchHit> hits, List<ReleaseGroup> artistAlbums) {
    }

    /**
     * Counts to rank with ({@code all}), the ones ListenBrainz just sent, to store ({@code fetched}), and
     * whether ListenBrainz failed, so the ranking has no counts at all ({@code failed}).
     */
    private record Listeners(Map<UUID, Integer> all, Map<UUID, Integer> fetched, boolean failed) {
    }

    private static SearchResponse response(List<AlbumRow> albums, boolean partial) {
        return new SearchResponse(albums.stream().map(AlbumSummary::from).toList(), partial);
    }
}
