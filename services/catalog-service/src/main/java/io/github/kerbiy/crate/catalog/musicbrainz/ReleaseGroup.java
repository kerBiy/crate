package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.List;
import java.util.UUID;

/**
 * An album as catalog-service sees it, independent of MusicBrainz's JSON shape.
 *
 * @param id               release-group MBID
 * @param artistCredit     display string with MusicBrainz's join phrases, e.g. "JAY-Z & Kanye West"
 * @param primaryType      "Album" or "EP" (the client drops everything else)
 * @param secondaryTypes   e.g. ["Soundtrack"] or ["Compilation", "Live"]; empty for a plain album
 * @param firstReleaseDate possibly partial ("1997", "1997-05") or null when unknown
 * @param releaseCount     how many releases (editions, pressings) the group has: a popularity signal.
 *                         Null when unknown: MusicBrainz only reports it in search results.
 * @param artists          credited artists in credit order; the first is the primary artist
 */
public record ReleaseGroup(
        UUID id,
        String title,
        String artistCredit,
        String primaryType,
        List<String> secondaryTypes,
        String firstReleaseDate,
        Integer releaseCount,
        List<ArtistCredit> artists) {

    public ReleaseGroup {
        secondaryTypes = List.copyOf(secondaryTypes);
        artists = List.copyOf(artists);
    }
}
