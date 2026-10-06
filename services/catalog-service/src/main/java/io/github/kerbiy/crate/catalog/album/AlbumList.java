package io.github.kerbiy.crate.catalog.album;

import java.util.List;

/** Response of the batch endpoint. */
record AlbumList(List<AlbumSummary> items) {
}
