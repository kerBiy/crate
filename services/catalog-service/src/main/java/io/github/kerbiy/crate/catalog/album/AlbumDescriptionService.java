package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzClient;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzException;
import io.github.kerbiy.crate.catalog.musicbrainz.WikiLinks;
import io.github.kerbiy.crate.catalog.web.Problems;
import io.github.kerbiy.crate.catalog.wikipedia.WikipediaClient;
import io.github.kerbiy.crate.catalog.wikipedia.WikipediaException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The album's Wikipedia intro, as a read-through cache (ADR 014): found the first time someone opens the
 * album (its MusicBrainz links, then Wikidata, then Wikipedia: up to three requests), then served from our
 * DB for 30 days. "No English article" is stored too, so it isn't asked again on every view.
 * Like {@link AlbumTracksService}, no {@code @Transactional}: nothing outside is called inside a transaction.
 */
@Service
class AlbumDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(AlbumDescriptionService.class);

    private final AlbumService albums;
    private final AlbumDescriptionRepository descriptions;
    private final MusicBrainzClient musicBrainz;
    private final WikipediaClient wikipedia;
    private final AlbumProperties properties;

    AlbumDescriptionService(AlbumService albums, AlbumDescriptionRepository descriptions, MusicBrainzClient musicBrainz,
            WikipediaClient wikipedia, AlbumProperties properties) {
        this.albums = albums;
        this.descriptions = descriptions;
        this.musicBrainz = musicBrainz;
        this.wikipedia = wikipedia;
        this.properties = properties;
    }

    /** 404 for an album MusicBrainz doesn't know; 503 when MusicBrainz or Wikimedia can't be reached for it. */
    AlbumDescription get(UUID id) {
        // By the stored id: for a merged MBID it is the surviving release group's.
        UUID albumId = albums.get(id).id();
        return descriptions.find(albumId, properties.descriptionRefreshAfter())
                .orElseGet(() -> fetchAndStore(albumId));
    }

    private AlbumDescription fetchAndStore(UUID albumId) {
        AlbumDescription description;
        try {
            description = englishTitle(albumId)
                    .flatMap(wikipedia::summary)
                    .map(summary -> new AlbumDescription(summary.extract(), summary.url().toString(), summary.title()))
                    .orElse(AlbumDescription.NONE);
        } catch (MusicBrainzException | WikipediaException e) {
            // Nothing stored, so the next view tries again. The page simply shows no description meanwhile.
            log.warn("Wikipedia description of {} failed: {}", albumId, e.getMessage());
            throw Problems.exception(HttpStatus.SERVICE_UNAVAILABLE, "description-unavailable",
                    "Description unavailable", "Couldn't load the album's description. Try again later.");
        }
        descriptions.store(albumId, description);
        return description;
    }

    /** A direct English Wikipedia link on MusicBrainz wins; otherwise the English article of its Wikidata item. */
    private Optional<String> englishTitle(UUID albumId) {
        Optional<WikiLinks> links = musicBrainz.wikiLinks(albumId);
        if (links.isEmpty()) {
            return Optional.empty();
        }
        if (links.get().englishTitle() != null) {
            return Optional.of(links.get().englishTitle());
        }
        String wikidataId = links.get().wikidataId();
        return wikidataId == null ? Optional.empty() : wikipedia.englishTitle(wikidataId);
    }
}
