package io.github.kerbiy.crate.catalog.album;

import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzClient;
import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzException;
import io.github.kerbiy.crate.catalog.listenbrainz.ListenBrainzProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps an album's ListenBrainz numbers fresh when its page is viewed, without anyone waiting for them.
 *
 * <p>{@code GET /albums/{id}} is also review-service's "does this album exist?" check before a rating is
 * saved, so it must never wait on optional data. A stale album gets one ListenBrainz call on a virtual
 * thread and the request answers at once with what is stored. The set of albums in flight makes a reload
 * (or two friends opening the same page) join the running call instead of starting another.
 */
@Component
class ListenBrainzRefresher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ListenBrainzRefresher.class);

    private final ListenBrainzClient listenBrainz;
    private final AlbumRepository albums;
    private final ListenBrainzProperties properties;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    ListenBrainzRefresher(ListenBrainzClient listenBrainz, AlbumRepository albums, ListenBrainzProperties properties) {
        this.listenBrainz = listenBrainz;
        this.albums = albums;
        this.properties = properties;
    }

    /**
     * Starts a refresh if the album's numbers are missing or older than {@code refresh-after}.
     *
     * @return true if numbers are on their way (just started, or already running)
     */
    boolean refreshIfStale(AlbumRow album) {
        if (!isStale(album)) {
            return false;
        }
        // add() is atomic: of two requests at the same moment, only one gets true and submits.
        if (inFlight.add(album.id())) {
            threads.submit(() -> refresh(album.id()));
        }
        return true;
    }

    private boolean isStale(AlbumRow album) {
        Instant checkedAt = album.listenersCheckedAt();
        return checkedAt == null
                || album.listenCount() == null
                || checkedAt.isBefore(Instant.now().minus(properties.refreshAfter()));
    }

    private void refresh(UUID id) {
        try {
            albums.recordPopularity(listenBrainz.popularity(List.of(id)));
        } catch (ListenBrainzException e) {
            // Nothing stored: the next view tries again. The page simply shows no numbers meanwhile.
            log.warn("ListenBrainz refresh of {} failed: {}", id, e.getMessage());
        } catch (RuntimeException e) {
            log.error("ListenBrainz refresh of {} failed", id, e);
        } finally {
            inFlight.remove(id);
        }
    }

    /** On shutdown: stop waiting for calls in flight. Their albums are simply refreshed on a later view. */
    @Override
    public void close() {
        threads.shutdownNow();
    }
}
