package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.ArtistCredit;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzQueryBuilder;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.search.SearchText;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Albums and artists in catalog_db, in plain SQL. */
@Repository
public class AlbumRepository {

    // Album columns plus the rating aggregate; albums nobody rated have no album_stats row.
    private static final String SELECT_ALBUMS = """
            select a.id, a.title, a.artist_credit, a.primary_artist_id, a.primary_type, a.first_release_date,
                   coalesce(s.rating_count, 0) as rating_count, coalesce(s.rating_sum, 0) as rating_sum,
                   s.rating_distribution
            from albums a
            left join album_stats s on s.album_id = a.id
            """;

    private final JdbcClient jdbc;

    AlbumRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the albums and their artists, or refreshes them if they are already stored.
     *
     * <p>Safe when two requests store the same albums at the same time: {@code ON CONFLICT DO UPDATE}
     * turns the loser's insert into an update instead of a unique-key error. Rows are written in
     * MBID order, so concurrent transactions lock them in the same order and can't deadlock.
     */
    @Transactional
    public void upsertAll(Collection<ReleaseGroup> releaseGroups) {
        List<ReleaseGroup> albums = distinctSortedById(releaseGroups, ReleaseGroup::id);
        List<ArtistCredit> artists = distinctSortedById(
                albums.stream().flatMap(album -> album.artists().stream()).filter(a -> a.id() != null).toList(),
                ArtistCredit::id);

        // Artists first: albums.primary_artist_id references them.
        for (ArtistCredit artist : artists) {
            jdbc.sql("""
                            insert into artists (id, name, sort_name) values (:id, :name, :sortName)
                            on conflict (id) do update set name = excluded.name, sort_name = excluded.sort_name
                            """)
                    .param("id", artist.id())
                    .param("name", artist.name())
                    .param("sortName", artist.sortName())
                    .update();
        }
        for (ReleaseGroup album : albums) {
            UUID primaryArtistId = album.artists().isEmpty() ? null : album.artists().getFirst().id();
            jdbc.sql("""
                            insert into albums (id, title, artist_credit, primary_artist_id, primary_type,
                                                secondary_types, first_release_date, release_count,
                                                search_text, fetched_at)
                            values (:id, :title, :artistCredit, :primaryArtistId, :primaryType,
                                    :secondaryTypes, :firstReleaseDate, :releaseCount, :searchText, now())
                            on conflict (id) do update set
                                title = excluded.title,
                                artist_credit = excluded.artist_credit,
                                primary_artist_id = excluded.primary_artist_id,
                                primary_type = excluded.primary_type,
                                secondary_types = excluded.secondary_types,
                                first_release_date = excluded.first_release_date,
                                -- A lookup by id doesn't report the count: keep the last known one.
                                release_count = coalesce(excluded.release_count, albums.release_count),
                                search_text = excluded.search_text,
                                fetched_at = excluded.fetched_at
                            """)
                    .param("id", album.id())
                    .param("title", album.title())
                    .param("artistCredit", album.artistCredit())
                    .param("primaryArtistId", primaryArtistId)
                    .param("primaryType", album.primaryType())
                    .param("secondaryTypes", album.secondaryTypes().toArray(String[]::new))
                    .param("releaseCount", album.releaseCount())
                    .param("firstReleaseDate", album.firstReleaseDate())
                    .param("searchText", SearchText.forAlbum(album.title(), album.artistCredit()))
                    .update();
        }
    }

    /**
     * The albums MusicBrainz ranked for this query (see SearchCacheRepository#store), best first.
     * Albums confirmed to have no cover are left out; unchecked ones stay.
     */
    public List<AlbumRow> findRanked(String normalizedQuery, int limit) {
        return jdbc.sql(SELECT_ALBUMS + """
                        join search_results r on r.album_id = a.id
                        where r.normalized_query = :q
                          and a.has_cover is not false
                        order by r.rank
                        limit :limit
                        """)
                .param("q", normalizedQuery)
                .param("limit", limit)
                .query(AlbumRepository::mapRow)
                .list();
    }

    /**
     * Fallback for when MusicBrainz can't be reached: stored albums whose {@code search_text} contains
     * something close to {@code normalizedQuery}, best first, most released first among equals.
     *
     * <p>{@code <%} is pg_trgm's word-similarity operator: true when some stretch of search_text shares
     * enough trigrams with the query (at least {@code threshold}, 0–1). It's the form the GIN index
     * can answer. The threshold is a session setting, so it is set for this transaction only.
     *
     * <p>Excluded secondary types are left out unless the title is the query. {@code lower(title)}
     * keeps accents, so "bjork" won't count as Björk's exact title here; good enough for a fallback.
     */
    @Transactional(readOnly = true)
    public List<AlbumRow> search(String normalizedQuery, double threshold, int limit) {
        jdbc.sql("select set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
                .param("threshold", Double.toString(threshold))
                .query(String.class)
                .single();
        return jdbc.sql(SELECT_ALBUMS + """
                        where :q <% a.search_text
                          and a.has_cover is not false
                          and (not (coalesce(a.secondary_types, '{}') && cast(:excluded as text[]))
                               or lower(a.title) = :q)
                        order by word_similarity(:q, a.search_text) desc,
                                 a.release_count desc nulls last,
                                 similarity(:q, a.search_text) desc,
                                 a.title, a.id
                        limit :limit
                        """)
                .param("q", normalizedQuery)
                .param("excluded", MusicBrainzQueryBuilder.EXCLUDED_SECONDARY_TYPES.toArray(String[]::new))
                .param("limit", limit)
                .query(AlbumRepository::mapRow)
                .list();
    }

    /**
     * Which of these albums need asking the Cover Art Archive: never checked, or "no cover" older
     * than {@code recheckAfter} (people upload covers later). A known cover is never rechecked.
     */
    public List<UUID> needingCoverCheck(Collection<UUID> ids, Duration recheckAfter) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select id from albums
                        where id in (:ids)
                          and (has_cover is null
                               or (not has_cover and cover_checked_at < now() - make_interval(secs => :seconds)))
                        """)
                .param("ids", ids)
                .param("seconds", recheckAfter.toSeconds())
                .query(UUID.class)
                .list();
    }

    /** Stores what the Cover Art Archive said. Rows are updated in id order, like {@link #upsertAll}. */
    @Transactional
    public void recordCovers(Map<UUID, Boolean> hasCover) {
        hasCover.keySet().stream().sorted().forEach(id -> jdbc.sql("""
                        update albums set has_cover = :hasCover, cover_checked_at = now() where id = :id
                        """)
                .param("hasCover", hasCover.get(id))
                .param("id", id)
                .update());
    }

    public Optional<AlbumRow> findById(UUID id) {
        return jdbc.sql(SELECT_ALBUMS + "where a.id = :id")
                .param("id", id)
                .query(AlbumRepository::mapRow)
                .optional();
    }

    /** The stored albums among {@code ids}, in no particular order; unknown ids are skipped. */
    public List<AlbumRow> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // A collection parameter expands to (?, ?, ...).
        return jdbc.sql(SELECT_ALBUMS + "where a.id in (:ids)")
                .param("ids", ids)
                .query(AlbumRepository::mapRow)
                .list();
    }

    private static <T> List<T> distinctSortedById(Collection<T> items, Function<T, UUID> id) {
        // One search often credits the same artist on several albums: write each row once.
        Map<UUID, T> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(id.apply(item), item));
        return byId.values().stream().sorted(Comparator.comparing(id)).toList();
    }

    private static AlbumRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AlbumRow(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("artist_credit"),
                rs.getObject("primary_artist_id", UUID.class),
                rs.getString("primary_type"),
                rs.getString("first_release_date"),
                rs.getInt("rating_count"),
                rs.getInt("rating_sum"),
                distribution(rs.getArray("rating_distribution")));
    }

    /** Ratings per half-star value, ½ star first; ten zeros when the album has no album_stats row. */
    private static List<Integer> distribution(Array array) throws SQLException {
        if (array == null) {
            return AlbumRow.NO_RATINGS;
        }
        return List.of((Integer[]) array.getArray());
    }
}
