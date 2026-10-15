package io.github.kerbiy.crate.catalog.musicbrainz;

import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.CreditJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.GenreJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.LabelInfoJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.LabelJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.MediumJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.ReleaseBrowseJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.ReleaseGroupJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.ReleaseJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.RelationJson;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.SearchResponse;
import io.github.kerbiy.crate.catalog.musicbrainz.MusicBrainzResponses.UrlRelsJson;
import io.github.kerbiy.crate.catalog.search.SearchText;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Rate-limited, retrying client for the MusicBrainz web service. Hands out only release groups
 * whose primary type is Album or EP, mapped to our own {@link ReleaseGroup}. Lookups by id return
 * any secondary type (someone may rate a live album); searches leave the excluded ones out.
 *
 * <p>Blocking by design (callers run on the request thread). Never call it inside a DB transaction:
 * with the rate limiter and retries one call can take several seconds.
 */
public class MusicBrainzClient {

    private static final Set<String> ALBUM_TYPES = Set.of("Album", "EP");
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final int NOT_FOUND = 404;
    private static final String NO_LABEL = "[no label]";
    /** One browse page, MusicBrainz's maximum. Groups with more releases are judged on their first 100 (ADR 013). */
    static final int RELEASE_BROWSE_LIMIT = 100;
    private static final Pattern WIKIDATA_ITEM = Pattern.compile("https?://(?:www\\.)?wikidata\\.org/wiki/(Q\\d+)");
    private static final Pattern ENGLISH_WIKIPEDIA = Pattern.compile("https?://en\\.(?:m\\.)?wikipedia\\.org/wiki/(.+)");

    private final RestClient restClient;
    private final RateLimiter rateLimiter;
    private final MusicBrainzProperties.Retry retry;
    private final Sleeper sleeper;
    private final DoubleSupplier random;

    MusicBrainzClient(RestClient restClient, RateLimiter rateLimiter, MusicBrainzProperties.Retry retry,
            Sleeper sleeper, DoubleSupplier random) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
        this.retry = retry;
        this.sleeper = sleeper;
        this.random = random;
    }

    /**
     * Builds the client from configuration. Tests use it too, with a fake rate limiter, sleeper and
     * random source, so they exercise the same HTTP setup as production without waiting.
     *
     * @param random returns values in [0, 1); used for backoff jitter
     */
    public static MusicBrainzClient create(MusicBrainzProperties properties, RateLimiter rateLimiter,
            Sleeper sleeper, DoubleSupplier random) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new MusicBrainzClient(restClient, rateLimiter, properties.retry(), sleeper, random);
    }

    /**
     * Searches release groups for what the user typed, e.g. "Radiohead - OK Computer", in
     * MusicBrainz's order (best score first). Albums and EPs only; compilations, live albums etc.
     * only when their title is exactly what was typed.
     *
     * @param fuzzy allow small misspellings (see {@link MusicBrainzQueryBuilder#build})
     */
    public List<SearchHit> searchReleaseGroups(String userInput, boolean fuzzy, int limit) {
        MusicBrainzQueryBuilder.Query query = MusicBrainzQueryBuilder.build(userInput, fuzzy);
        String exactTitle = SearchText.normalize(query.title());
        return search(query.lucene(), limit).stream()
                .filter(MusicBrainzClient::isAlbum)
                .filter(json -> !isExcluded(json) || SearchText.normalize(json.title()).equals(exactTitle))
                .map(json -> new SearchHit(toReleaseGroup(json), json.score() == null ? 0 : json.score()))
                .toList();
    }

    /** One artist's albums, excluded secondary types left out, in no useful order. */
    public List<ReleaseGroup> artistAlbums(UUID artistId, int limit) {
        return search(MusicBrainzQueryBuilder.artistAlbums(artistId), limit).stream()
                .filter(MusicBrainzClient::isAlbum)
                .filter(json -> !isExcluded(json))
                .map(MusicBrainzClient::toReleaseGroup)
                .toList();
    }

    private List<ReleaseGroupJson> search(String luceneQuery, int limit) {
        // The query is a URI variable, so it's fully percent-encoded: Lucene's &, + and \ arrive intact.
        SearchResponse response = get(uri -> uri.path("/release-group")
                        .queryParam("query", "{query}")
                        .queryParam("limit", limit)
                        .queryParam("fmt", "json")
                        .build(Map.of("query", luceneQuery)),
                SearchResponse.class)
                .orElseThrow(() -> new MusicBrainzException("MusicBrainz search answered 404"));
        return response.releaseGroups() == null ? List.of() : response.releaseGroups();
    }

    /** Looks up one release group. Empty when MusicBrainz doesn't know it or it isn't an Album/EP. */
    public Optional<ReleaseGroup> lookupReleaseGroup(UUID mbid) {
        return get(uri -> uri.path("/release-group/{id}")
                        .queryParam("inc", "artist-credits")
                        .queryParam("fmt", "json")
                        .build(mbid),
                ReleaseGroupJson.class)
                .filter(MusicBrainzClient::isAlbum)
                .map(MusicBrainzClient::toReleaseGroup);
    }

    /**
     * The release group's official releases (at most {@link #RELEASE_BROWSE_LIMIT}, one page, in no
     * useful order), with their labels, track counts and links to listening platforms (ADR 015). A group with no official release (only
     * bootlegs or promos) gets one more request for all of them. Empty when MusicBrainz knows none.
     */
    public List<ReleaseSummary> releases(UUID releaseGroupId) {
        List<ReleaseSummary> official = browseReleases(releaseGroupId, true);
        return official.isEmpty() ? browseReleases(releaseGroupId, false) : official;
    }

    private List<ReleaseSummary> browseReleases(UUID releaseGroupId, boolean officialOnly) {
        ReleaseBrowseJson browse = get(uri -> {
                    uri.path("/release")
                            .queryParam("release-group", releaseGroupId)
                            .queryParam("inc", "labels+media+url-rels")
                            .queryParam("limit", RELEASE_BROWSE_LIMIT)
                            .queryParam("fmt", "json");
                    if (officialOnly) {
                        uri.queryParam("status", "official");
                    }
                    return uri.build();
                },
                ReleaseBrowseJson.class)
                .orElse(null);
        if (browse == null || browse.releases() == null) {
            return List.of();
        }
        return browse.releases().stream()
                .map(json -> new ReleaseSummary(json.id(), blankToNull(json.date()), blankToNull(json.country()),
                        media(json).stream().mapToInt(m -> m.trackCount() == null ? 0 : m.trackCount()).sum(),
                        labels(json), listenLinks(json)))
                .toList();
    }

    /** One release with its tracks, labels and the release group's genres. Empty when MusicBrainz doesn't know it. */
    public Optional<Release> release(UUID releaseId) {
        return get(uri -> uri.path("/release/{id}")
                        .queryParam("inc", "recordings+labels+release-groups+genres")
                        .queryParam("fmt", "json")
                        .build(releaseId),
                ReleaseJson.class)
                .map(MusicBrainzClient::toRelease);
    }

    /**
     * Where the release group's Wikipedia article can be found (ADR 014): its Wikidata item and a direct
     * English Wikipedia link, each null when MusicBrainz has none. Links marked as ended are left out, and
     * Wikipedias in other languages too. Empty when MusicBrainz doesn't know the group.
     */
    public Optional<WikiLinks> wikiLinks(UUID releaseGroupId) {
        return get(uri -> uri.path("/release-group/{id}")
                        .queryParam("inc", "url-rels")
                        .queryParam("fmt", "json")
                        .build(releaseGroupId),
                UrlRelsJson.class)
                .map(MusicBrainzClient::toWikiLinks);
    }

    /**
     * One logical request: every attempt waits for the rate limiter, 503 is retried with jittered
     * backoff, 404 is empty, anything else that isn't 2xx is an error.
     */
    private <T> Optional<T> get(Function<UriBuilder, URI> uri, Class<T> type) {
        for (int attempt = 1; ; attempt++) {
            rateLimiter.acquire();
            Answer<T> answer = send(uri, type);
            if (answer.status() != SERVICE_UNAVAILABLE) {
                return Optional.ofNullable(answer.body());
            }
            if (attempt >= retry.maxAttempts()) {
                throw new MusicBrainzException("MusicBrainz still answered 503 after " + attempt + " attempts");
            }
            sleep(backoff(attempt));
        }
    }

    private <T> Answer<T> send(Function<UriBuilder, URI> uri, Class<T> type) {
        try {
            return restClient.get().uri(uri).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (response.getStatusCode().is2xxSuccessful()) {
                    return new Answer<>(status, response.bodyTo(type));
                }
                if (status == NOT_FOUND || status == SERVICE_UNAVAILABLE) {
                    return new Answer<>(status, null);
                }
                throw new MusicBrainzException("MusicBrainz answered " + status);
            });
        } catch (RestClientException e) {
            // Timeouts, refused connections, unreadable JSON.
            throw new MusicBrainzException("MusicBrainz request failed", e);
        }
    }

    /**
     * Exponential backoff with "equal jitter": half of the delay is fixed, the other half random.
     * Attempt 1 → 0.5–1 s, attempt 2 → 1–2 s (with a 1 s initial backoff). The fixed half
     * guarantees a real pause; the random half stops concurrent callers from retrying in lockstep.
     */
    Duration backoff(int attempt) {
        long delay = retry.initialBackoff().toNanos() << (attempt - 1);
        long half = delay / 2;
        return Duration.ofNanos(half + (long) (random.getAsDouble() * (delay - half)));
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MusicBrainzException("Interrupted while backing off", e);
        }
    }

    private static boolean isAlbum(ReleaseGroupJson json) {
        return ALBUM_TYPES.contains(json.primaryType());
    }

    private static boolean isExcluded(ReleaseGroupJson json) {
        return json.secondaryTypes() != null
                && json.secondaryTypes().stream().anyMatch(MusicBrainzQueryBuilder.EXCLUDED_SECONDARY_TYPES::contains);
    }

    private static ReleaseGroup toReleaseGroup(ReleaseGroupJson json) {
        List<CreditJson> credits = json.artistCredit() == null ? List.of() : json.artistCredit();
        // "JAY-Z" + " & " + "Kanye West" + "": names as credited, glued by MusicBrainz's join phrases.
        String artistCredit = credits.stream()
                .map(c -> c.name() + (c.joinphrase() == null ? "" : c.joinphrase()))
                .collect(Collectors.joining());
        List<ArtistCredit> artists = credits.stream()
                .map(CreditJson::artist)
                .map(a -> new ArtistCredit(a.id(), a.name(), a.sortName()))
                .toList();
        String date = json.firstReleaseDate() == null || json.firstReleaseDate().isBlank()
                ? null : json.firstReleaseDate();
        List<String> secondaryTypes = json.secondaryTypes() == null ? List.of() : json.secondaryTypes();
        return new ReleaseGroup(json.id(), json.title(), artistCredit, json.primaryType(), secondaryTypes, date,
                json.count(), artists);
    }

    private static Release toRelease(ReleaseJson json) {
        List<String> labels = labels(json);
        List<MediumJson> withTracks = media(json).stream()
                .filter(medium -> medium.tracks() != null && !medium.tracks().isEmpty())
                .sorted(Comparator.comparing(medium -> medium.position() == null ? Integer.MAX_VALUE : medium.position()))
                .toList();
        // Numbered 1, 2, … after leaving out empty media, so "Disc 2" is the second one shown.
        List<Release.Disc> discs = new ArrayList<>();
        for (MediumJson medium : withTracks) {
            discs.add(new Release.Disc(discs.size() + 1, medium.tracks().stream()
                    .map(track -> new Release.Track(track.position(), blankToNull(track.number()),
                            track.title(), track.length()))
                    .toList()));
        }
        List<GenreJson> genres = json.releaseGroup() == null || json.releaseGroup().genres() == null
                ? List.of() : json.releaseGroup().genres();
        List<String> topGenres = genres.stream()
                .filter(genre -> genre.name() != null && genre.count() != null && genre.count() > 0)
                .sorted(Comparator.comparing(GenreJson::count).reversed().thenComparing(GenreJson::name))
                .map(GenreJson::name)
                .limit(Release.MAX_GENRES)
                .toList();
        return new Release(json.id(), labels.isEmpty() ? null : labels.getFirst(), discs, topGenres);
    }

    private static WikiLinks toWikiLinks(UrlRelsJson json) {
        List<RelationJson> current = json.relations() == null ? List.of() : json.relations().stream()
                .filter(relation -> !Boolean.TRUE.equals(relation.ended()))
                .filter(relation -> relation.url() != null && relation.url().resource() != null)
                .toList();
        String wikidataId = firstMatch(current, "wikidata", WIKIDATA_ITEM);
        String title = firstMatch(current, "wikipedia", ENGLISH_WIKIPEDIA);
        // The link's path is the title, percent-encoded and with underscores for spaces.
        String englishTitle = title == null ? null
                : URLDecoder.decode(title.replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' ');
        return new WikiLinks(wikidataId, englishTitle);
    }

    private static String firstMatch(List<RelationJson> relations, String type, Pattern pattern) {
        return relations.stream()
                .filter(relation -> type.equals(relation.type()))
                .map(relation -> pattern.matcher(relation.url().resource()))
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse(null);
    }

    /** The release's current links to listening platforms, the first one per platform. Ended links are left out. */
    private static List<ListenLink> listenLinks(ReleaseJson json) {
        if (json.relations() == null) {
            return List.of();
        }
        return json.relations().stream()
                .filter(relation -> !Boolean.TRUE.equals(relation.ended()))
                .filter(relation -> relation.url() != null && relation.url().resource() != null)
                .map(relation -> ListenPlatform.recognize(relation.url().resource()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(ListenLink::platform, link -> link, (first, second) -> first,
                        () -> new EnumMap<>(ListenPlatform.class)))
                .values().stream()
                .toList();
    }

    private static List<MediumJson> media(ReleaseJson json) {
        return json.media() == null ? List.of() : json.media();
    }

    /** Label names in credit order, each once. "[no label]" is MusicBrainz's way of saying there is none. */
    private static List<String> labels(ReleaseJson json) {
        if (json.labelInfo() == null) {
            return List.of();
        }
        return json.labelInfo().stream()
                .map(LabelInfoJson::label)
                .filter(label -> label != null && label.name() != null && !label.name().equals(NO_LABEL))
                .map(LabelJson::name)
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** What one HTTP attempt produced: the status, and the body when it was 2xx. */
    private record Answer<T>(int status, T body) {
    }
}
