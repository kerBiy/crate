package io.github.kerbiy.crate.catalog.search;

import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Which normalized queries MusicBrainz already answered, and when. */
@Repository
class SearchCacheRepository {

    private final JdbcClient jdbc;

    SearchCacheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True if MusicBrainz answered this query less than {@code ttl} ago. Uses the DB clock, like {@link #record}. */
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

    /** Upsert: two requests recording the same query at once both succeed; the later one wins. */
    void record(String normalizedQuery) {
        jdbc.sql("""
                        insert into search_cache (normalized_query, fetched_at) values (:q, now())
                        on conflict (normalized_query) do update set fetched_at = excluded.fetched_at
                        """)
                .param("q", normalizedQuery)
                .update();
    }
}
