package io.github.kerbiy.crate.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.kerbiy.crate.catalog.album.AlbumRepository;
import io.github.kerbiy.crate.catalog.musicbrainz.ArtistCredit;
import io.github.kerbiy.crate.catalog.musicbrainz.ReleaseGroup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Base for catalog integration tests: real Postgres, WireMock as MusicBrainz, a clean slate per test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public abstract class CatalogIntegrationTest {

    protected static final String MB_SEARCH = "/ws/2/release-group";

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected WireMockServer musicBrainz;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected AlbumRepository albums;

    @BeforeEach
    void resetState() {
        jdbc.sql("truncate albums, artists, album_stats, search_cache, search_results").update();
        musicBrainz.resetAll();
    }

    protected static ResponseDefinitionBuilder fixture(String name) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBodyFile("musicbrainz/" + name);
    }

    protected static ResponseDefinitionBuilder emptySearch() {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"release-groups\": []}");
    }

    /** Stores an album through the real write path, as if MusicBrainz had returned it. */
    protected ReleaseGroup seed(String title, String artist) {
        return seed(title, artist, 1, List.of());
    }

    protected ReleaseGroup seed(String title, String artist, int releaseCount, List<String> secondaryTypes) {
        ReleaseGroup album = new ReleaseGroup(UUID.randomUUID(), title, artist, "Album", secondaryTypes, "1997-05-21",
                releaseCount, List.of(new ArtistCredit(UUID.randomUUID(), artist, artist)));
        albums.upsertAll(List.of(album));
        return album;
    }

    protected int count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }
}
