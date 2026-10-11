package io.github.kerbiy.crate.catalog.search;

import io.github.kerbiy.crate.catalog.album.AlbumSummary;
import java.util.List;

/**
 * @param nextCursor where the next page starts, or null on the last page
 * @param partial    true when MusicBrainz should have been asked but couldn't be: the items are only
 *                   what we had stored, so the client may want to say "results may be incomplete"
 */
record SearchResponse(List<AlbumSummary> items, String nextCursor, boolean partial) {
}
