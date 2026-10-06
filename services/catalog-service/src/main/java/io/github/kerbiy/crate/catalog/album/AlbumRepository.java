package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.ArtistCredit;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.search.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                   coalesce(s.rating_count, 0) as rating_count, coalesce(s.rating_sum, 0) as rating_sum
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
                                                first_release_date, search_text, fetched_at)
                            values (:id, :title, :artistCredit, :primaryArtistId, :primaryType,
                                    :firstReleaseDate, :searchText, now())
                            on conflict (id) do update set
                                title = excluded.title,
                                artist_credit = excluded.artist_credit,
                                primary_artist_id = excluded.primary_artist_id,
                                primary_type = excluded.primary_type,
                                first_release_date = excluded.first_release_date,
                                search_text = excluded.search_text,
                                fetched_at = excluded.fetched_at
                            """)
                    .param("id", album.id())
                    .param("title", album.title())
                    .param("artistCredit", album.artistCredit())
                    .param("primaryArtistId", primaryArtistId)
                    .param("primaryType", album.primaryType())
                    .param("firstReleaseDate", album.firstReleaseDate())
                    .param("searchText", SearchText.forAlbum(album.title(), album.artistCredit()))
                    .update();
        }
    }

    /**
     * Albums whose {@code search_text} contains something close to {@code normalizedQuery}, best first.
     *
     * <p>{@code <%} is pg_trgm's word-similarity operator: true when some stretch of search_text shares
     * enough trigrams with the query (at least {@code threshold}, 0–1). It's the form the GIN index
     * can answer. The threshold is a session setting, so it is set for this transaction only.
     */
    @Transactional(readOnly = true)
    public List<AlbumRow> search(String normalizedQuery, double threshold, int limit) {
        jdbc.sql("select set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
                .param("threshold", Double.toString(threshold))
                .query(String.class)
                .single();
        return jdbc.sql(SELECT_ALBUMS + """
                        where :q <% a.search_text
                        order by word_similarity(:q, a.search_text) desc,
                                 similarity(:q, a.search_text) desc,
                                 a.title, a.id
                        limit :limit
                        """)
                .param("q", normalizedQuery)
                .param("limit", limit)
                .query(AlbumRepository::mapRow)
                .list();
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
                rs.getInt("rating_sum"));
    }
}
