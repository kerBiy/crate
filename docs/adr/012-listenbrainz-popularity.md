# ADR-012: ListenBrainz listener counts in search ranking

Date: 2026-10-11 · Status: accepted (extends ADR-010's relevance formula; keeps ADR-011's MusicBrainz-only source)

## Context

ADR-011 kept MusicBrainz as the only album source, but Deezer still ranked better on mainstream queries
(main set hit@1 29/30 vs our 24/30) because it has real popularity data. MusicBrainz's only popularity
signal is the number of releases (ADR-010). That favours albums with many old pressings over the
album people actually mean, especially for short titles ("am", "25", "currents").

ListenBrainz (also MetaBrainz) publishes listen and listener counts per release group, keyed by the
**same MBIDs**. Its `POST /1/popularity/release-group` endpoint takes up to 1000 MBIDs per call and
needs no token. So we can add popularity without a second source of truth or any ID matching.

## Decision

On a cold search, after MusicBrainz answers and before ranking:

1. Look up stored listener counts for the query's results; those fetched in the last 30 days are reused.
2. Fetch the rest in **one** ListenBrainz batch call (no call if nothing is missing):
   - 400 ms timeout, no retry
   - its own 1 call/s rate limiter (ListenBrainz's documented limit)
   - the MusicBrainz User-Agent
   - body sent with a Content-Length (ListenBrainz rejects chunked bodies)
3. Rank the query's results by **MusicBrainz score + 15·ln(1 + releases) + 2·ln(1 + listeners)**
   (formula C, `crate.catalog.search.listener-weight: 2`). The artist path ("radiohead") stays ordered by
   release count; ties go by earliest first release (unknown last), then by MBID (added 2026-10-12: they
   used to keep MusicBrainz's order, which changes from call to call, e.g. "frank sinatra" in the
   Step 2 eval).
4. If ListenBrainz fails in any way (timeout, 4xx/5xx including 429, network error, full rate-limit
   queue), rank with **no** listener counts, not even stored ones. That is exactly the ranking from
   before this ADR. The search never fails and never waits more than the timeout because of ListenBrainz.
   That ranking is cached for **24 hours** instead of 7 days (`search_cache.without_listeners`, V7;
   `cache-ttl-without-listeners`), so an outage doesn't keep worse rankings for a week.
5. Store the fetched counts on `albums` (`listener_count`, `listeners_checked_at`; V6). V6 also clears
   `search_cache`, so old rankings are rebuilt.

## Results

**Offline (Step 1, `search-eval/results/2026-10-11-listenbrainz.md`):**
- Method:
  - A Python mirror of the pipeline kept every query's candidates (parity with the endpoint: 63/64 queries).
  - Nine formulas re-ranked them, tuned on the main set only.
  - Held-out (25 queries, written before any ListenBrainz data was fetched) and niche (9) were then scored
    once with the parameters fixed.
- The rule: held-out and niche must not get worse. Only C passed:

| Formula | main hit@1 / MRR | held-out hit@1 / hit@3 / MRR | held-out artist nDCG@5 |
|---|---|---|---|
| today | 24 / 0.861 | 19 / 24 / 0.863 | 0.91 |
| **C** (w = 2) | **26 / 0.894** | **22 / 25 / 0.933** | 0.91 |
| B: score + 8·ln(1+listeners) | 26 / 0.894 | 20 / 25 / 0.887 | 0.91 |
| D: score + 5·ln(1+listens) | 26 / 0.894 | 20 / 24 / 0.883 | 0.91 |
| E2: artist path by listens | 27 / 0.917 | 18 / 24 / 0.830 | **0.67** |

Niche stayed at 9/9, MRR 0.944, for every formula.

**Endpoint (Step 2, `search-eval/results/2026-10-11-step2.md`):** before/after on the real endpoint,
back to back, each on an empty database:

| Set | hit@1 | hit@3 | MRR | artist nDCG@5 |
|---|---|---|---|---|
| main | 24 → 26 | 28 → 28 | 0.867 → 0.900 | 0.805 → 0.805 |
| held-out | 19 → 21 | 24 → 25 | 0.863 → 0.913 | 0.908 → 0.870 |
| niche | 8 → 8 | 9 → 9 | 0.944 → 0.944 | – |

- Improved: "21", "radiohed", "am" (4 → 1), "25" and "currents".
- Held-out nDCG@5 dropped only because of a release-count tie in the artist path, which listeners don't
  touch. "frank sinatra" put a Christmas album and *Songs for Swingin' Lovers!* (25 releases each) in
  MusicBrainz's order, which changes from call to call.
- ListenBrainz: 58 calls, 0 failures, median 65 ms.
- Cold-search latency: unchanged within MusicBrainz's noise (~2 s median).

## Alternatives considered

- **Listeners or listens instead of release count (B, D).** Tie C on main. C stays at its best for any
  weight from 2 to 10, while B and D lose hit@3 below 6 and 5. With no counts, C is exactly the old
  ranking, so its fallback costs nothing.
- **Popularity in the artist path (E, E2, H): token-free, by scoring the albums we already fetch.**
  Main improves a lot (artist nDCG@5 0.78 → 0.96), but held-out gets worse (0.91 → 0.67):
  - For Elvis Presley, Frank Sinatra and The Rolling Stones, the most-played "albums" are greatest-hits
    sets MusicBrainz doesn't flag as compilations, and Christmas albums.
  - By listeners, Black Panther: The Album came first for Kendrick Lamar.
- **ListenBrainz's `top-release-groups-for-artist`.** Needs a user token (401 without one). Simulated
  over complete artist album lists (G), it equals the token-free E2 on every metric of every set, so it
  is no better by any margin.
- **A relevance floor (F: only boost when score ≥ s).** Worse on main (hit@3 27/30) and held-out (21/25), equal on niche.
- **Deezer.** Rejected in ADR-011 (niche coverage 5/9, terms).

## Addendum 2026-10-14: the numbers on the album page

The album page shows listeners and total listens ("25.3k listeners, 1.8m listens on ListenBrainz").
Same client, same 400 ms timeout, no retry, same limiter; `listen_count` is stored next to
`listener_count` (V8), by search too.

On the album page the refresh runs **in the background**:
- `GET /albums/{id}` is also review-service's "does this album exist?" check before a rating is saved,
  so it must never wait on optional data.
- When the numbers are missing or older than 30 days, catalog starts one call on a virtual thread and
  answers at once with what it has, plus `listenBrainzRefreshing: true`. The page asks again once,
  1.5 s later.
- A set of albums in flight makes concurrent views join the running call instead of starting another.
- A failure stores nothing; the next view tries again. The page shows no numbers meanwhile.
- `GET /albums?ids=` and search never trigger it.

Rejected: a separate "lite" existence path for review-service. It would only protect callers that
remember to use it, and would change review-service's contract.

## Deferred: pre-warming from sitewide top lists

The idea:
- A resumable job seeds popular albums from `GET /1/stats/sitewide/release-groups` (no token) through
  MusicBrainz.
- Searches answer from that local pool first and refresh from MusicBrainz in the background
  (stale-while-revalidate).

Not now, because:
- The list stops at the **top 1000 per range** (week, month, year, all time; offset 1000 returns
  nothing), so a few thousand albums in total.
- The queries ranking can't fix ("blue", "red") are a recall problem: the expected albums aren't in
  MusicBrainz's 100 results at all. The pool would only half help:
  - Taylor Swift's *Red* is #314 in the all-time list.
  - Joni Mitchell's *Blue* isn't in it.
- Seeding 1000 albums is about 17 minutes of MusicBrainz's 1 request/s.

Revisit after the cover cache (ADR-011's decision), which is the bigger cold-search cost.

## License and terms

**Confirmed on primary pages:**
- [metabrainz.org/datasets/postgres-dumps](https://metabrainz.org/datasets/postgres-dumps) lists the
  ListenBrainz data dumps as "Licenses: Creative Commons Zero (CC0)". The
  [dump docs](https://listenbrainz.readthedocs.io/en/latest/users/listenbrainz-dumps.html) say the public
  dump holds "statistics derived from listens".
- The same page: "Commercial use: Allowed, but financial support strongly urged, even for CC0 data".
- The [social contract](https://metabrainz.org/social-contract): "The factual database content will
  be in the public domain". Listener counts are factual.

**Not confirmed:** no primary page states a license for **API responses** as such
(`/1/popularity/release-group`). We rely on the API serving aggregates of CC0-dumped data, which are
facts.

**Not to confuse:** the "Music Listening History Dataset+" on
[derived-dumps](https://metabrainz.org/datasets/derived-dumps) is non-commercial only. We don't use it.

**Costs and limits:**
- Free while Crate is non-commercial. Companies with revenue are asked to become supporters (tiers from
  $100/month).
- [Rate limit](https://listenbrainz.readthedocs.io/en/latest/users/api/index.html): the docs ask for
  at most 1 call/s; the response headers allowed 30 per ~10 s when measured. We follow the docs.

## Consequences

Good:
- Popular albums come first where MusicBrainz's relevance can't tell (short titles, typos): main hit@1
  24 → 26, held-out 19 → 21, niche unchanged.
- Still one source of albums and one ID. ListenBrainz only adds a number.
- ListenBrainz being down or slow costs at most 400 ms and the old ranking. Tests cover slow, 500, 429
  and dropped connections.

Bad:
- A cold search makes one more outbound call (median ~65 ms, at most 400 ms plus up to 1 s of
  rate-limiter wait when cold searches come less than a second apart).
- During a ListenBrainz outage, each query searched is asked from MusicBrainz again once a day instead of
  once a week, spending more of its 1 request/s. Its rankings lack listeners for up to 24 hours after
  ListenBrainz is back.
- Counts can be up to 30 days old, and albums ListenBrainz doesn't know count as 0 (today's behaviour
  for them).
- Listener counts reflect ListenBrainz's users, a smaller and more technical crowd than Deezer's.
- The artist-path tie-break (earliest first) is a rule, not a measure of popularity: between two albums
  with equal releases, the older one wins even if the newer one is better known.
