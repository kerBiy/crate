package io.github.kerbiy.crate.catalog.album;

import java.util.List;
import java.util.UUID;

/**
 * Everything the album page needs. {@code ratingDistribution}: how many ratings each half-star value
 * got, 10 entries, ½ star first and 5 stars last (the histogram).
 *
 * @param listenBrainz           ListenBrainz's numbers, not ours; null when unknown (never fetched, the
 *                               fetch failed, or ListenBrainz has no listens for it)
 * @param listenBrainzRefreshing they are being fetched right now, in the background: ask again in a moment
 * @param release                facts of the release the tracklist comes from (ADR 013); null until
 *                               someone opened the tracklist, or when MusicBrainz has no release
 * @param genres                 the release group's genres, most votes first; empty until then too
 */
record AlbumDetails(
        UUID id,
        String title,
        String artistCredit,
        UUID primaryArtistId,
        String primaryType,
        String firstReleaseDate,
        String year,
        String coverUrl,
        Double avgRating,
        int ratingCount,
        List<Integer> ratingDistribution,
        ListenBrainzNumbers listenBrainz,
        boolean listenBrainzRefreshing,
        ReleaseFacts release,
        List<String> genres) {

    static AlbumDetails from(AlbumRow album, boolean listenBrainzRefreshing) {
        return new AlbumDetails(album.id(), album.title(), album.artistCredit(), album.primaryArtistId(),
                album.primaryType(), album.firstReleaseDate(), album.year(), CoverArt.front500(album.id()),
                album.avgRating(), album.ratingCount(), album.ratingDistribution(),
                ListenBrainzNumbers.of(album), listenBrainzRefreshing, ReleaseFacts.of(album), album.genres());
    }

    record ListenBrainzNumbers(int listeners, long listens) {

        /** 0 listeners means ListenBrainz doesn't know the album: show nothing rather than "0". */
        static ListenBrainzNumbers of(AlbumRow album) {
            if (album.listenerCount() == null || album.listenerCount() == 0 || album.listenCount() == null) {
                return null;
            }
            return new ListenBrainzNumbers(album.listenerCount(), album.listenCount());
        }
    }

    /** {@code lengthMs} is null when a track has no length on MusicBrainz. */
    record ReleaseFacts(String label, int trackCount, Long lengthMs) {

        static ReleaseFacts of(AlbumRow album) {
            if (album.releaseId() == null) {
                return null;
            }
            return new ReleaseFacts(album.label(), album.trackCount() == null ? 0 : album.trackCount(), album.lengthMs());
        }
    }
}
