package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.album.AlbumRepository;
import io.github.kerbiy.crate.catalog.album.AlbumRow;
import io.github.kerbiy.crate.catalog.album.AlbumSummary;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Album search as a read-through cache (SPEC 5.3): our DB answers; MusicBrainz is asked only when
 * the DB doesn't have enough and we haven't asked about this query recently.
 *
 * <p>No {@code @Transactional} here on purpose: the MusicBrainz call can take seconds and must not
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
        // 1. Same normalization as albums.search_text, so "Björk" and "bjork" meet.
        String normalized = SearchText.normalize(query);
        if (normalized.isEmpty()) {
            throw Problems.invalidField("q", "must contain letters or digits");
        }

        // 2-3. Enough local matches, or MusicBrainz answered this query recently: done.
        List<AlbumRow> local = searchLocally(normalized, limit);
        if (local.size() >= Math.min(properties.goodMatches(), limit)
                || searchCache.isFresh(normalized, properties.cacheTtl())) {
            return response(local, false);
        }

        // 4. Ask MusicBrainz (outside any transaction), store what it knows, search again.
        List<ReleaseGroup> fetched;
        try {
            fetched = musicBrainz.searchReleaseGroups(query, properties.musicBrainzLimit());
        } catch (MusicBrainzException e) {
            // 5. Never fail a search because MusicBrainz is down: serve what we have, flagged.
            log.warn("MusicBrainz search for '{}' failed, returning local results: {}", normalized, e.getMessage());
            return response(local, true);
        }
        albums.upsertAll(fetched);
        // Recorded even when MusicBrainz found nothing, so a nonsense query doesn't hit it every time.
        searchCache.record(normalized);
        return response(searchLocally(normalized, limit), false);
    }

    private List<AlbumRow> searchLocally(String normalized, int limit) {
        return albums.search(normalized, properties.matchThreshold(), limit);
    }

    private static SearchResponse response(List<AlbumRow> albums, boolean partial) {
        return new SearchResponse(albums.stream().map(AlbumSummary::from).toList(), partial);
    }
}
