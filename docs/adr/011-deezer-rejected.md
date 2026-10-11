# ADR-011: Deezer evaluated as album source, rejected

Date: 2026-10-11 · Status: accepted (keeps ADR-003's MusicBrainz source)

## Context

Search on MusicBrainz is slow on a cold query (median 1.7 s, ~1 request/second shared rate limit) and
covers come from the Cover Art Archive, which is slow to load. Deezer's public API has its own search,
popularity ranking and fast CDN covers, so a spike (branch `spike/deezer`, not merged) put a
`/albums/search-deezer` endpoint next to ours and scored both with the search eval.

Deezer returns no MusicBrainz IDs, so its hits were matched by normalized title + one shared artist
name (`eval.py endpoint --match title`). Ours were matched by MBID, as in ADR-010.

## Evaluation

**Main set** (`queries.json`, 30 queries; the set ADR-010 was tuned on, so ours is optimistic):

| Category | MusicBrainz hit@3 | Deezer hit@3 | MusicBrainz MRR | Deezer MRR |
|---|---|---|---|---|
| Artist only | 4/4 | 4/4 | 0.88 | 1.00 |
| Many compilations | 4/4 | 4/4 | 1.00 | 1.00 |
| Title only | 6/6 | 6/6 | 0.92 | 1.00 |
| Artist + title | 4/4 | 4/4 | 1.00 | 1.00 |
| Short titles | 2/4 | 4/4 | 0.38 | 1.00 |
| Typos | 4/4 | 3/4 | 0.88 | 0.79 |
| Diacritics | 4/4 | 4/4 | 1.00 | 1.00 |
| **All** | **28/30** | **29/30** | **0.867** | **0.971** |
| Latency, cold | median 1689 ms, max 2760 ms | median 381 ms, max 899 ms | | |
| Latency, warm | median 12 ms (cached) | median 340 ms (no cache) | | |

**Niche set** (`queries-niche.json`, 9 queries chosen before any Deezer run: Romanian, on Bandcamp
but not Spotify, pre-1970):

| Category | MusicBrainz hit@3 | Deezer hit@3 | MusicBrainz MRR | Deezer MRR |
|---|---|---|---|---|
| Romanian | 3/3 | 2/3 | 1.00 | 0.67 |
| Bandcamp | 3/3 | 2/3 | 1.00 | 0.67 |
| Pre-1970 | 3/3 | 1/3 | 0.83 | 0.37 |
| **All** | **9/9** | **5/9** | **0.944** | **0.567** |

Deezer ranks popular albums better (it has real popularity data; "blue" and "red" work) and answers
faster. But it only knows what is licensed for streaming on it: 4 of the 9 niche albums are missing
from its top 3, 3 from its top 10. Crate is for logging *any* album, and these are the ones people
come to a site like this to log.

## Cover load times

TODO

## Terms of use

TODO

## Decision

Stay on MusicBrainz as the only album source. Fix cover speed by serving covers ourselves: fetch each
album's cover from the Cover Art Archive once, store it, and serve it from our own storage, instead of
having browsers load it from the Cover Art Archive.

## Alternatives considered

- **Deezer as the only source.** Better ranking and speed on mainstream queries, but loses niche
  albums (5/9) and has the terms-of-use problems above.
- **MusicBrainz for data, Deezer only for covers.** Albums don't share an ID; matching by title +
  artist would be needed on every album, and the terms-of-use problems remain.
- **Deezer first, MusicBrainz as fallback.** Two sources of truth for the same album, with different
  IDs and metadata; duplicates in search and in users' logs.

## Consequences

Good:
- One source, one ID (the MBID) for every album, including niche ones.
- Covers become as fast as our own storage, and don't depend on the Cover Art Archive at view time.

Bad:
- Cold search stays at ~1.7 s median; only the cache makes repeat queries fast.
- Short single-word titles ("blue", "red") stay weak: MusicBrainz has no popularity signal beyond the
  release count (ADR-010).
- We store and serve images, which costs storage and needs a fetch job.
