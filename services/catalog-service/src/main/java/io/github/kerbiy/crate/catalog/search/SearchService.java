package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.album.AlbumRepository;
import io.github.kerbiy.crate.catalog.album.AlbumRow;
import io.github.kerbiy.crate.catalog.album.AlbumSummary;
import io.github.kerbiy.crate.catalog.coverart.CoverArtProperties;
import io.github.kerbiy.crate.catalog.coverart.CoverChecker;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.musicbrainz.SearchHit;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Album search as a read-through cache (SPEC 5.3, ADR 010). MusicBrainz ranks; we store its ranked
 * answer per query and replay it until the cache entry expires. Albums the Cover Art Archive has no
 * cover for are left out of the answer.
 *
 * <p>No {@code @Transactional} here on purpose: the MusicBrainz calls can take seconds and must not
 * hold a DB transaction (and connection) open. Each repository call is its own short transaction.
 */
@Service
class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final AlbumRepository albums;
    private final SearchCacheRepository searchCache;
    private final MusicBrainzClient musicBrainz;
    private final CoverChecker covers;
    private final SearchProperties properties;
    private final CoverArtProperties coverArt;

    SearchService(AlbumRepository albums, SearchCacheRepository searchCache, MusicBrainzClient musicBrainz,
            CoverChecker covers, SearchProperties properties, CoverArtProperties coverArt) {
        this.albums = albums;
        this.searchCache = searchCache;
        this.musicBrainz = musicBrainz;
        this.covers = covers;
        this.properties = properties;
        this.coverArt = coverArt;
    }

    SearchResponse search(String query, int limit) {
        // 1. Same normalization as albums.search_text, so "Björk" and "bjork" share a cache entry.
        String normalized = SearchText.normalize(query);
        if (normalized.isEmpty()) {
            throw Problems.invalidField("q", "must contain letters or digits");
        }

        // 2. MusicBrainz answered this query recently: replay its ranking.
        if (searchCache.isFresh(normalized, properties.cacheTtl())) {
            return response(albums.findRanked(normalized, limit), false);
        }

        // 3. Ask MusicBrainz (outside any transaction), store the albums and the ranking.
        List<ReleaseGroup> ranked;
        try {
            ranked = askMusicBrainz(query, normalized);
        } catch (MusicBrainzException e) {
            // 4. Never fail a search because MusicBrainz is down: serve what we have, flagged.
            log.warn("MusicBrainz search for '{}' failed, returning local results: {}", normalized, e.getMessage());
            return response(albums.search(normalized, properties.matchThreshold(), limit), true);
        }
        albums.upsertAll(ranked);
        checkCovers(ranked);
        // Recorded even when MusicBrainz found nothing, so a nonsense query doesn't hit it every time.
        searchCache.store(normalized, ranked.stream().map(ReleaseGroup::id).toList());
        return response(albums.findRanked(normalized, limit), false);
    }

    /**
     * One to three MusicBrainz requests: the query itself; the same query allowing misspellings, only
     * if the first found nothing; and the artist's albums, if the query is an artist's name.
     */
    private List<ReleaseGroup> askMusicBrainz(String query, String normalized) {
        int limit = properties.musicBrainzLimit();
        List<SearchHit> hits = musicBrainz.searchReleaseGroups(query, false, limit);
        if (hits.isEmpty()) {
            hits = musicBrainz.searchReleaseGroups(query, true, limit);
        }
        List<ReleaseGroup> artistAlbums = SearchRanking.matchingArtist(normalized, hits)
                .map(artist -> musicBrainz.artistAlbums(artist, limit))
                .orElse(List.of());
        return SearchRanking.merge(artistAlbums, hits, properties.popularityWeight());
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

    private static SearchResponse response(List<AlbumRow> albums, boolean partial) {
        return new SearchResponse(albums.stream().map(AlbumSummary::from).toList(), partial);
    }
}
