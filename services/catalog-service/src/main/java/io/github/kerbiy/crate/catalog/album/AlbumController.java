package io.github.kerbiy.crate.catalog.album;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/albums")
class AlbumController {

    private final AlbumService albums;
    private final AlbumTracksService tracks;
    private final AlbumDescriptionService descriptions;
    private final ListenBrainzRefresher listenBrainz;

    AlbumController(AlbumService albums, AlbumTracksService tracks, AlbumDescriptionService descriptions,
            ListenBrainzRefresher listenBrainz) {
        this.albums = albums;
        this.tracks = tracks;
        this.descriptions = descriptions;
        this.listenBrainz = listenBrainz;
    }

    /** The album page, and review-service's existence check: never waits on ListenBrainz. */
    @GetMapping("/{id}")
    AlbumDetails get(@PathVariable UUID id) {
        AlbumRow album = albums.get(id);
        return AlbumDetails.from(album, listenBrainz.refreshIfStale(album));
    }

    /** Fetched lazily by the album page; may take a couple of seconds the first time (MusicBrainz). */
    @GetMapping("/{id}/tracks")
    AlbumTracks tracks(@PathVariable UUID id) {
        return tracks.get(id);
    }

    /** Fetched lazily by the album page; the first time, up to three requests (MusicBrainz, Wikidata, Wikipedia). */
    @GetMapping("/{id}/description")
    AlbumDescription description(@PathVariable UUID id) {
        return descriptions.get(id);
    }

    /** {@code GET /albums?ids=a,b,c}: used to render feeds. Spring splits the comma-separated list. */
    @GetMapping
    AlbumList getMany(@RequestParam @NotEmpty @Size(max = 100) List<UUID> ids) {
        return new AlbumList(albums.getMany(ids).stream().map(AlbumSummary::from).toList());
    }
}
