package io.github.kerbiy.crate.catalog.album;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Each album's Wikipedia intro, or the fact that it has none (album_descriptions), in plain SQL. */
@Repository
class AlbumDescriptionRepository {

    private final JdbcClient jdbc;

    AlbumDescriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The stored description, also when it is "no article". Empty when there is none yet, or when it was
     * fetched {@code refreshAfter} ago or longer: the article may have changed, or been written since.
     */
    Optional<AlbumDescription> find(UUID albumId, Duration refreshAfter) {
        return jdbc.sql("""
                        select text, article_url, article_title, fetched_at
                        from album_descriptions where album_id = :id
                        """)
                .param("id", albumId)
                .query((rs, rowNum) -> new Stored(
                        new AlbumDescription(rs.getString("text"), rs.getString("article_url"), rs.getString("article_title")),
                        rs.getObject("fetched_at", OffsetDateTime.class).toInstant()))
                .optional()
                .filter(stored -> !stored.fetchedAt().isBefore(Instant.now().minus(refreshAfter)))
                .map(Stored::description);
    }

    /**
     * Stores what was found, replacing what was there. One statement, so two first views of the same
     * album at once are safe: the second upsert simply overwrites the first with the same answer.
     */
    void store(UUID albumId, AlbumDescription description) {
        jdbc.sql("""
                        insert into album_descriptions (album_id, text, article_url, article_title, fetched_at)
                        values (:id, :text, :articleUrl, :articleTitle, now())
                        on conflict (album_id) do update set
                            text = excluded.text,
                            article_url = excluded.article_url,
                            article_title = excluded.article_title,
                            fetched_at = excluded.fetched_at
                        """)
                .param("id", albumId)
                .param("text", description.text())
                .param("articleUrl", description.articleUrl())
                .param("articleTitle", description.articleTitle())
                .update();
    }

    private record Stored(AlbumDescription description, Instant fetchedAt) {
    }
}
