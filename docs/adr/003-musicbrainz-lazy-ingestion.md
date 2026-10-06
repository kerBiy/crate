# ADR-003: MusicBrainz as the catalog source, with on-demand ingestion

Date: 2026-10-05 · Status: accepted

## Context

Crate needs album data: titles, artists, release dates and cover art. Building this catalog by hand is impossible, so it has to come from an external source.

Constraints:
- The source must be free and usable by a small app with no business account.
- Development happens on a laptop with little disk space and RAM, and production runs on a small free server.
- The app must keep working, at least partly, when the external source is slow or down.

## Decision

- Use **MusicBrainz** as the catalog source. Albums are MusicBrainz *release groups*, and their IDs (MBIDs) are used directly as primary keys in our database, so every service can refer to an album without a mapping table.
- Use **Cover Art Archive** for cover images, linked by MBID. Images are not stored by us.
- **Ingest on demand** (read-through cache) instead of importing the full database:
  1. A search looks in our own database first.
  2. If there are too few good results, and the same search wasn't made recently, catalog-service asks MusicBrainz, saves the albums it gets back, and searches again.
  3. Opening an album that isn't stored yet fetches it once and saves it.
- Respect MusicBrainz's rules: a meaningful User-Agent, and **about one request per second**, enforced in catalog-service with a token bucket. On HTTP 503, back off and retry a limited number of times.
  The token bucket is hand-written (capacity 1, no dependency) so it can be explained and tested with a fake clock. Retries: 3 attempts in total, exponential backoff with jitter, only on 503; every attempt waits for the bucket.
- If MusicBrainz fails, search returns local results marked as partial. **A search never fails just because MusicBrainz is down.**
- An optional seed job loads a list of a few hundred albums at startup, so the catalog isn't empty on day one.

## Alternatives considered

- **Spotify Web API.** Rich data, but access for new apps has been restricted and its terms are tied to Spotify's platform. Rejected: too risky for a project that must stay free and stable.
- **Importing the full MusicBrainz database dump.** Complete and fast to query, but large: it doesn't fit comfortably on the laptop or the free server, and keeping it in sync is its own project. Rejected for now; can be reconsidered if the server gets bigger.
- **Calling MusicBrainz live on every request, storing nothing.** Simplest, but slow, limited to one request per second for all users together, and the app breaks whenever MusicBrainz is down. Rejected.

## Consequences

Good:
- Real, open data at no cost, with stable IDs shared across all services.
- The local catalog grows with what users actually search for and stays small.
- Popular searches are answered from our own database, quickly and without using the rate limit.
- A clear, interview-worthy problem: integrating an external API under a strict rate limit, with caching and graceful degradation.

Bad:
- The first search for something new is slower, since it waits for MusicBrainz.
- The one-request-per-second limit is shared by all users. Many new searches at once will queue up or return partial results.
- The token bucket lives inside one catalog-service instance. Running several instances would need a shared (distributed) rate limiter.
- Stored album data can become outdated. A periodic refresh is planned for a later phase.
- Search quality depends on how well queries are translated to MusicBrainz's search syntax.
