package io.github.kerbiy.crate.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ListenPlatformTest {

    @ParameterizedTest
    @CsvSource({
            "https://open.spotify.com/album/6dVIqQ8qmQ5GBnJ9shOYGE, SPOTIFY",
            "https://music.apple.com/gb/album/1097861387, APPLE_MUSIC",
            "https://itunes.apple.com/gb/album/id697194953, APPLE_MUSIC",
            "https://music.youtube.com/playlist?list=OLAK5uy_nc6afxSnmK8OFML8dF9q_0D1MBMXsGnL4, YOUTUBE_MUSIC",
            "https://www.deezer.com/album/14879699, DEEZER",
            "https://tidal.com/album/58990510, TIDAL",
            "https://radiohead.bandcamp.com/album/ok-computer, BANDCAMP",
            "https://soundcloud.com/kendrick-lamar-music/sets/damn, SOUNDCLOUD",
    })
    void recognizesAlbumLinks(String url, ListenPlatform platform) {
        assertThat(ListenPlatform.recognize(url)).contains(new ListenLink(platform, url));
    }

    @Test
    void aYouTubeAlbumPlaylistOpensOnYouTubeMusic() {
        assertThat(ListenPlatform.recognize("https://www.youtube.com/playlist?list=OLAK5uy_mnQGurOeWdgT_jzJ9XXxL78jgJ4lBOFQc"))
                .contains(new ListenLink(ListenPlatform.YOUTUBE_MUSIC,
                        "https://music.youtube.com/playlist?list=OLAK5uy_mnQGurOeWdgT_jzJ9XXxL78jgJ4lBOFQc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.discogs.com/release/83182",
            "https://www.amazon.co.uk/gp/product/B000002UJQ",
            "https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb", // an artist, not an album
            "https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", // someone's playlist
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "not a url",
    })
    void ignoresEverythingElse(String url) {
        assertThat(ListenPlatform.recognize(url)).isEmpty();
    }
}
