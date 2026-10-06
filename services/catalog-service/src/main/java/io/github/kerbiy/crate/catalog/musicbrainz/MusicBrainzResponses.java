package io.github.kerbiy.crate.catalog.musicbrainz;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * MusicBrainz's JSON, only the fields we read (Jackson 3 ignores unknown properties by default).
 * Package-private: nothing outside the client should depend on MusicBrainz's shape.
 */
final class MusicBrainzResponses {

    private MusicBrainzResponses() {
    }

    record SearchResponse(@JsonProperty("release-groups") List<ReleaseGroupJson> releaseGroups) {
    }

    record ReleaseGroupJson(
            UUID id,
            String title,
            @JsonProperty("primary-type") String primaryType,
            @JsonProperty("first-release-date") String firstReleaseDate,
            @JsonProperty("artist-credit") List<CreditJson> artistCredit) {
    }

    /** {@code name} is the name as credited, which can differ from the artist's own name. */
    record CreditJson(String name, String joinphrase, ArtistJson artist) {
    }

    record ArtistJson(UUID id, String name, @JsonProperty("sort-name") String sortName) {
    }
}
