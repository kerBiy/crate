package io.github.kerbiy.crate.catalog.stats;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * album_stats and processed_events, in plain SQL.
 *
 * <p>The distribution (ratings per value) is the source of truth; count and sum are always
 * recomputed from it, so the three can't disagree. Changes are read-modify-write, made safe by
 * {@link #lockDistribution} locking the row until the transaction ends.
 */
@Repository
class AlbumStatsRepository {

    private final JdbcClient jdbc;

    AlbumStatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True the first time this event id is seen; false if it was already processed. */
    boolean markProcessed(UUID eventId) {
        return jdbc.sql("insert into processed_events (event_id) values (:id) on conflict do nothing")
                .param("id", eventId)
                .update() == 1;
    }

    /**
     * The album's ratings per value (index 0 = rating 1 = ½ star), locked with {@code select ... for
     * update} so nobody else changes them before {@link #save}. Creates the row on the album's first
     * event. No FK to albums: an event may arrive before catalog has stored the album (SPEC 5.3).
     */
    int[] lockDistribution(UUID albumId) {
        jdbc.sql("insert into album_stats (album_id) values (:albumId) on conflict do nothing")
                .param("albumId", albumId)
                .update();
        Integer[] distribution = jdbc.sql("select rating_distribution from album_stats where album_id = :albumId for update")
                .param("albumId", albumId)
                .query((rs, row) -> (Integer[]) rs.getArray(1).getArray())
                .single();
        return Arrays.stream(distribution).mapToInt(Integer::intValue).toArray();
    }

    /** Stores the distribution, with count and sum derived from it. */
    void save(UUID albumId, int[] distribution) {
        int count = 0;
        int sum = 0;
        for (int i = 0; i < distribution.length; i++) {
            count += distribution[i];
            sum += distribution[i] * (i + 1);
        }
        jdbc.sql("""
                        update album_stats
                        set rating_distribution = :distribution, rating_count = :count, rating_sum = :sum
                        where album_id = :albumId
                        """)
                .param("albumId", albumId)
                .param("distribution", Arrays.stream(distribution).boxed().toArray(Integer[]::new))
                .param("count", count)
                .param("sum", sum)
                .update();
    }
}
