package io.github.kerbiy.crate.catalog.musicbrainz;

import java.util.UUID;

/** One artist credited on a release group. {@code id} is the MusicBrainz artist MBID. */
public record ArtistCredit(UUID id, String name, String sortName) {
}
