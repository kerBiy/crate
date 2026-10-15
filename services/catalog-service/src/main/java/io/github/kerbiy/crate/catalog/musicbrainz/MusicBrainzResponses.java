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

    /** {@code score} (relevance) and {@code count} (number of releases) are only in search results. */
    record ReleaseGroupJson(
            UUID id,
            String title,
            @JsonProperty("primary-type") String primaryType,
            @JsonProperty("secondary-types") List<String> secondaryTypes,
            @JsonProperty("first-release-date") String firstReleaseDate,
            @JsonProperty("artist-credit") List<CreditJson> artistCredit,
            Integer score,
            Integer count) {
    }

    /** {@code name} is the name as credited, which can differ from the artist's own name. */
    record CreditJson(String name, String joinphrase, ArtistJson artist) {
    }

    record ArtistJson(UUID id, String name, @JsonProperty("sort-name") String sortName) {
    }

    /** A browse of a release group's releases: one page, in no useful order. */
    record ReleaseBrowseJson(@JsonProperty("release-count") Integer releaseCount, List<ReleaseJson> releases) {
    }

    /**
     * One release. A browse with {@code inc=labels+media+url-rels} fills {@code labelInfo}, {@code media}
     * (track counts only) and {@code relations} (its links to other sites); a lookup with {@code inc=recordings+labels+release-groups+genres} also fills the
     * tracks and the release group with its genres.
     */
    record ReleaseJson(
            UUID id,
            String date,
            String country,
            @JsonProperty("label-info") List<LabelInfoJson> labelInfo,
            List<MediumJson> media,
            @JsonProperty("release-group") ReleaseGroupGenresJson releaseGroup,
            List<RelationJson> relations) {
    }

    record LabelInfoJson(LabelJson label) {
    }

    record LabelJson(String name) {
    }

    record MediumJson(Integer position, @JsonProperty("track-count") Integer trackCount, List<TrackJson> tracks) {
    }

    /** {@code length} in milliseconds, null when unknown; {@code number} as printed ("A1"). */
    record TrackJson(Integer position, String number, String title, Integer length) {
    }

    record ReleaseGroupGenresJson(List<GenreJson> genres) {
    }

    /** {@code count}: how many MusicBrainz users voted for this genre. */
    record GenreJson(String name, Integer count) {
    }

    /** A release group looked up with {@code inc=url-rels}: its links to other sites. */
    record UrlRelsJson(List<RelationJson> relations) {
    }

    /** {@code type}: "wikidata", "wikipedia", "free streaming", "streaming"…; {@code ended}: the link is marked as no longer valid. */
    record RelationJson(String type, Boolean ended, UrlJson url) {
    }

    record UrlJson(String resource) {
    }
}
