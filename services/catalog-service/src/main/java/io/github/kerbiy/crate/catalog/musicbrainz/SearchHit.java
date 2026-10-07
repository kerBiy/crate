package io.github.kerbiy.crate.catalog.musicbrainz;

/**
 * One search result with MusicBrainz's relevance score (0–100) for the query that found it. The
 * score belongs to the query, not the album, which is why it isn't part of {@link ReleaseGroup}.
 */
public record SearchHit(ReleaseGroup releaseGroup, int score) {
}
