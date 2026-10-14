package io.github.kerbiy.crate.catalog.listenbrainz;

/**
 * What ListenBrainz knows about one release group: how many of its users listened to it, and how many
 * times in total. Both 0 when ListenBrainz doesn't know it.
 */
public record Popularity(int listeners, long listens) {

    public static final Popularity UNKNOWN = new Popularity(0, 0);
}
