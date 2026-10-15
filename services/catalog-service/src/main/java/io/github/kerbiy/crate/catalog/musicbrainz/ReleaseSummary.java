package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.List;
import java.util.UUID;

/**
 * One release of a release group, as much as a browse tells: enough to choose the one release the
 * album page reads from (see {@link ReleaseChoice}) and its listening links (see {@link ListenLinkChoice}).
 *
 * @param date       possibly partial ("1997", "1997-06") or null when unknown
 * @param country    MusicBrainz country code, e.g. "GB", or "XE" (Europe), "XW" (worldwide); null when unknown
 * @param trackCount tracks on all media together
 * @param labels     label names, each once, without MusicBrainz's "[no label]"
 * @param links      its current links to listening platforms, at most one per platform
 */
public record ReleaseSummary(UUID id, String date, String country, int trackCount, List<String> labels,
        List<ListenLink> links) {

    public ReleaseSummary {
        labels = List.copyOf(labels);
        links = List.copyOf(links);
    }

    public ReleaseSummary(UUID id, String date, String country, int trackCount, List<String> labels) {
        this(id, date, country, trackCount, labels, List.of());
    }
}
