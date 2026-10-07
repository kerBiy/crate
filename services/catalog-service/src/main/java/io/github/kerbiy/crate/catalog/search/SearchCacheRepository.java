package io.github.kerbiy.crate.catalog.search;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Which normalized queries MusicBrainz already answered, when, and with which albums in which order. */
@Repository
class SearchCacheRepository {

    private final JdbcClient jdbc;

    SearchCacheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True if MusicBrainz answered this query less than {@code ttl} ago. Uses the DB clock, like {@link #store}. */
    boolean isFresh(String normalizedQuery, Duration ttl) {
        return jdbc.sql("""
                        select exists (
                            select 1 from search_cache
                            where normalized_query = :q and fetched_at > now() - make_interval(secs => :seconds)
                        )
                        """)
                .param("q", normalizedQuery)
                .param("seconds", ttl.toSeconds())
                .query(Boolean.class)
                .single();
    }

    /**
     * Records MusicBrainz's ranked answer for a query (possibly empty) and marks the query fresh, in
     * one transaction: a fresh query always has its results.
     *
     * <p>Safe when two requests store the same query at once: the cache row is an upsert, so the
     * second waits for the first's row lock, then replaces its results. Result rows are written in
     * album-id order, so two writers lock them in the same order and can't deadlock.
     *
     * @param rankedAlbumIds best first; the albums must already be stored
     */
    @Transactional
    void store(String normalizedQuery, List<UUID> rankedAlbumIds) {
        jdbc.sql("""
                        insert into search_cache (normalized_query, fetched_at) values (:q, now())
                        on conflict (normalized_query) do update set fetched_at = excluded.fetched_at
                        """)
                .param("q", normalizedQuery)
                .update();
        jdbc.sql("delete from search_results where normalized_query = :q").param("q", normalizedQuery).update();

        Map<UUID, Integer> rankById = new HashMap<>();
        for (UUID id : rankedAlbumIds) {
            rankById.putIfAbsent(id, rankById.size() + 1);
        }
        rankById.keySet().stream().sorted().forEach(id -> jdbc.sql("""
                        insert into search_results (normalized_query, album_id, rank) values (:q, :id, :rank)
                        """)
                .param("q", normalizedQuery)
                .param("id", id)
                .param("rank", rankById.get(id))
                .update());
    }
}
