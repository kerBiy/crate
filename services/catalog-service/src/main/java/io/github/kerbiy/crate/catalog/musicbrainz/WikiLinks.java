package io.github.kerbiy.crate.catalog.musicbrainz;

/**
 * A release group's links to its Wikipedia article (ADR 014), either of them null when MusicBrainz has none.
 *
 * @param wikidataId   the Wikidata item, e.g. "Q202996"
 * @param englishTitle the English Wikipedia article's title, from a direct link, e.g. "OK Computer"
 */
public record WikiLinks(String wikidataId, String englishTitle) {
}
