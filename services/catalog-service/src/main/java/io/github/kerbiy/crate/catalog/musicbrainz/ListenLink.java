package io.github.kerbiy.crate.catalog.musicbrainz;

/** A link that opens an album on a listening platform (ADR 015). */
public record ListenLink(ListenPlatform platform, String url) {
}
