package io.github.kerbiy.crate.catalog.album;

/**
 * {@code GET /albums/{id}/description}: the intro of the album's English Wikipedia article (ADR 014),
 * shown with a link to the article and its license (CC BY-SA). All null when there is no article.
 *
 * @param text         the article's first paragraph, plain text, unchanged
 * @param articleUrl   the article, credited as "From Wikipedia"
 * @param articleTitle the article's title, e.g. "OK Computer"
 */
record AlbumDescription(String text, String articleUrl, String articleTitle) {

    static final AlbumDescription NONE = new AlbumDescription(null, null, null);
}
