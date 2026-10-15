package io.github.kerbiy.crate.catalog.musicbrainz;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where an album can be listened to (ADR 015), recognized from a release's url-rels. The order is the
 * order the API lists them in. Links to anything else are ignored.
 */
public enum ListenPlatform {
    SPOTIFY,
    APPLE_MUSIC,
    YOUTUBE_MUSIC,
    DEEZER,
    TIDAL,
    BANDCAMP,
    SOUNDCLOUD;

    /** YouTube's auto-generated album playlists ("OLAK5uy_…") are the same album on YouTube Music. */
    private static final Pattern YOUTUBE_ALBUM = Pattern.compile("[?&]list=(OLAK5uy_[A-Za-z0-9_-]+)");

    /**
     * The platform and the link to use, e.g. a youtube.com album playlist becomes its music.youtube.com
     * address. Empty for links to other sites, and for links to something that isn't an album there
     * (a Spotify artist, a YouTube video).
     */
    static Optional<ListenLink> recognize(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();
        ListenPlatform platform = null;
        if (host.equals("open.spotify.com") && path.startsWith("/album/")) {
            platform = SPOTIFY;
        } else if ((host.equals("music.apple.com") || host.equals("itunes.apple.com")) && path.contains("/album/")) {
            platform = APPLE_MUSIC;
        } else if (host.equals("music.youtube.com") && path.equals("/playlist")) {
            platform = YOUTUBE_MUSIC;
        } else if ((host.equals("www.youtube.com") || host.equals("youtube.com")) && path.equals("/playlist")) {
            Matcher matcher = YOUTUBE_ALBUM.matcher(url);
            return matcher.find()
                    ? Optional.of(new ListenLink(YOUTUBE_MUSIC, "https://music.youtube.com/playlist?list=" + matcher.group(1)))
                    : Optional.empty();
        } else if ((host.equals("www.deezer.com") || host.equals("deezer.com")) && path.contains("/album/")) {
            platform = DEEZER;
        } else if ((host.equals("tidal.com") || host.equals("listen.tidal.com")) && path.contains("/album/")) {
            platform = TIDAL;
        } else if (host.endsWith(".bandcamp.com") && path.startsWith("/album/")) {
            platform = BANDCAMP;
        } else if (host.equals("soundcloud.com") && path.contains("/sets/")) {
            platform = SOUNDCLOUD;
        }
        return platform == null ? Optional.empty() : Optional.of(new ListenLink(platform, url.trim()));
    }
}
