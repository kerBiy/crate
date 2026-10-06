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

    AlbumController(AlbumService albums) {
        this.albums = albums;
    }

    @GetMapping("/{id}")
    AlbumDetails get(@PathVariable UUID id) {
        return AlbumDetails.from(albums.get(id));
    }

    /** {@code GET /albums?ids=a,b,c}: used to render feeds. Spring splits the comma-separated list. */
    @GetMapping
    AlbumList getMany(@RequestParam @NotEmpty @Size(max = 100) List<UUID> ids) {
        return new AlbumList(albums.getMany(ids).stream().map(AlbumSummary::from).toList());
    }
}
