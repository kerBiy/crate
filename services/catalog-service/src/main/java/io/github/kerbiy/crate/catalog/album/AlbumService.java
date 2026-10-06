package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import io.github.kerbiy.crate.catalog.web.Problems;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Album lookups. No {@code @Transactional} here on purpose: a miss calls MusicBrainz, which can take
 * seconds (rate limit, retries), and no DB transaction may stay open during that. Each repository
 * call runs in its own short transaction instead.
 */
@Service
class AlbumService {

    private static final Logger log = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumRepository albums;
    private final MusicBrainzClient musicBrainz;

    AlbumService(AlbumRepository albums, MusicBrainzClient musicBrainz) {
        this.albums = albums;
        this.musicBrainz = musicBrainz;
    }

    /** Local hit, or fetched from MusicBrainz and stored. 404 if MusicBrainz doesn't know it either. */
    AlbumRow get(UUID id) {
        return albums.findById(id)
                .or(() -> fetchAndStore(id))
                .orElseThrow(() -> Problems.exception(HttpStatus.NOT_FOUND, "album-not-found",
                        "Album not found", "No album with id " + id + "."));
    }

    /** Stored albums only, in the order asked for; unknown ids are left out. */
    List<AlbumRow> getMany(Collection<UUID> ids) {
        Map<UUID, AlbumRow> found = albums.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(AlbumRow::id, Function.identity()));
        return ids.stream().distinct().map(found::get).filter(Objects::nonNull).toList();
    }

    private Optional<AlbumRow> fetchAndStore(UUID id) {
        Optional<ReleaseGroup> releaseGroup;
        try {
            releaseGroup = musicBrainz.lookupReleaseGroup(id);
        } catch (MusicBrainzException e) {
            // Not a 404: we can't tell whether the album exists, and callers (review-service) must know that.
            log.warn("MusicBrainz lookup of {} failed: {}", id, e.getMessage());
            throw Problems.exception(HttpStatus.SERVICE_UNAVAILABLE, "catalog-source-unavailable",
                    "Catalog source unavailable", "The album isn't stored yet and MusicBrainz can't be reached. Try again later.");
        }
        releaseGroup.ifPresent(found -> albums.upsertAll(List.of(found)));
        // Read back by MusicBrainz's id: for a merged MBID it answers with the surviving release group.
        return releaseGroup.flatMap(found -> albums.findById(found.id()));
    }
}
