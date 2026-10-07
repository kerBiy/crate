package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.album.AlbumRepository;
import io.github.kerbiy.crate.catalog.album.AlbumRow;
import io.github.kerbiy.crate.catalog.album.AlbumSummary;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.musicbrainz.SearchHit;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Album search as a read-through cache (SPEC 5.3, ADR 010). MusicBrainz ranks; we store its ranked
 * answer per query and replay it until the cache entry expires.
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
    private final SearchProperties properties;

    SearchService(AlbumRepository albums, SearchCacheRepository searchCache, MusicBrainzClient musicBrainz,
            SearchProperties properties) {
        this.albums = albums;
        this.searchCache = searchCache;
        this.musicBrainz = musicBrainz;
        this.properties = properties;
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

    private static SearchResponse response(List<AlbumRow> albums, boolean partial) {
        return new SearchResponse(albums.stream().map(AlbumSummary::from).toList(), partial);
    }
}
