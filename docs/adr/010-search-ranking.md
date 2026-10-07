# ADR-010: Search ranking — let MusicBrainz rank, and cache its ranking

Date: 2026-10-07 · Status: accepted (refines step 2 of ADR-003's search flow)

## Context

Album search was poor: "radiohead" showed "Radiohead Box" above OK Computer. Before changing anything
we built an evaluation set (`services/catalog-service/search-eval`): 30 realistic queries, each with the
album(s) that should be in the top 3, identified by MusicBrainz ID. Categories: artist only, artists with
many compilations/live albums, title only, artist + title, short titles, typos, diacritics. Baseline:
**13/30 in the top 3**. Running it against both the real MusicBrainz API and our endpoint gave six causes:

1. **The right albums were never fetched.** We asked MusicBrainz for 25 results. Its score favours
   titles that match, so "radiohead" returned albums *called* Radiohead (EPs, a box set, bootlegs, covers
   by other bands), not Radiohead's albums. In 17/30 queries the expected album wasn't in those 25.
2. **Words were OR-ed** (Lucene's default): "kind of blue" matched 247,781 release groups.
3. **Compilations, live albums and remixes crowded results**: for artist queries, 6–9 of the top 10
   have a secondary type.
4. **MusicBrainz's score was thrown away.** We re-ranked by trigram similarity, where every album by an
   artist ties on the artist's name and the shortest string wins. There was no popularity signal at all.
5. **Five weak local matches skipped MusicBrainz**: 6/30 queries never reached it. "red" matched
   "Revolver"; "radiohead kid a" was answered by five stored Radiohead rows, without Kid A.
6. **No typo tolerance on MusicBrainz's side**: "radiohed" returned 0 results.

## Decision

MusicBrainz ranks, we cache its ranking per query (`search_results`), and replay it until the cache entry
expires (7 days).

- **Query** (`MusicBrainzQueryBuilder`): every word required, each in the title or the artist;
  `primarytype:(album OR ep)`; secondary types Compilation, Live, Remix, DJ-mix, Mixtape/Street, Demo,
  Interview, Audiobook and Audio drama excluded **unless the title is exactly the query**, so
  "at folsom prison" still finds the live album. Soundtracks stay (Help!, A Hard Day's Night).
  Lucene can't OR a pure negative (`*:*` isn't supported by MusicBrainz either), so the query is
  `(T AND NOT excluded) OR (T AND releasegroup:"phrase")`, and the client keeps only exact titles from
  the second branch. 100 results (MusicBrainz's maximum) instead of 25: still one request.
- **Typos**: only when the query finds nothing, retry with `word~` for words of 4+ letters.
- **Artist path**: if the whole query is the name of an artist credited in the results ("radiohead",
  "beatles", "bjork"), also fetch that artist's albums (`arid:`), most releases first, and put them
  **before** the general results. The general results are kept, so "blue" still lists albums titled Blue
  after the band Blue's albums.
- **Ranking** of the general results: `score + 15 × ln(1 + releases in the group)`. The release count
  is MusicBrainz's `count`: OK Computer has 39 releases, Kind of Blue 136, most junk has 1.
- **No local shortcut.** Every query not freshly cached asks MusicBrainz. The trigram search is now only
  the fallback when MusicBrainz is down (`partial: true`).
- The frontend searches only from 2 characters, after 350 ms without typing: each new query can cost up
  to 3 MusicBrainz requests.

**Choosing the popularity weight.** Same queries, cached MusicBrainz answers, only the weight changed:

| Weight | hit@3 | MRR |
|---|---|---|
| 5 | 25/30 | 0.754 |
| 10 | 27/30 | 0.793 |
| **15** | **28/30** | **0.844** |
| 20 | 28/30 | 0.883 |
| 30 | 28/30 | 0.900 |

15 is the smallest weight with the best hit@3. Higher weights improve MRR on these 30 queries but start
pulling other artists' popular records into title queries ("The Abbey Road EP" by Spiritualized for
"abbey road"). The weight was tuned on the same set it is scored on, so the numbers below are
optimistic for unseen queries; the smaller weight is the safer choice.

## Results

Endpoint, empty database, the 30 queries in order (pass 1 cold, pass 2 warm). Reports are in
`search-eval/results/`; compare them with `eval.py compare`.

| Category | Before hit@3 | After hit@3 | Before MRR | After MRR |
|---|---|---|---|---|
| Artist only | 0/4 | 4/4 | 0.03 | 0.88 |
| Many compilations | 1/4 | 4/4 | 0.13 | 1.00 |
| Title only | 3/6 | 6/6 | 0.51 | 0.92 |
| Artist + title | 3/4 | 4/4 | 0.63 | 0.88 |
| Short titles | 0/4 | 2/4 | 0.00 | 0.38 |
| Typos | 2/4 | 4/4 | 0.41 | 0.83 |
| Diacritics | 4/4 | 4/4 | 0.88 | 1.00 |
| **All** | **13/30** | **28/30** | **0.377** | **0.844** |

No query got worse. The warm pass now gives the same results as the cold one (before, the warm pass was
also 13/30, with the same bad results kept for 7 days).

Still failing: "blue" (Joni Mitchell) and "red" (Taylor Swift / King Crimson). Thousands of release
groups are called Blue or Red, and the expected albums aren't in MusicBrainz's top 100 for the word
alone; the band named Blue/Red also comes first through the artist path. Typing "blue joni mitchell"
works.

## Covers (added 2026-10-07)

Many results had no cover art (7 of 20 Radiohead results in a probe: tributes, covers EPs). Search now
leaves out albums the Cover Art Archive confirms have none (SPEC 5.3, *Cover check*).

- **Cheapest reliable check:** `HEAD /release-group/{id}/front-250`. It answers 307 (has a cover) or
  404 (none) in about 0.2 s, with no body, and the redirect isn't followed. Fetching the image itself
  takes about 2 s.
- **Once per album:** `albums.has_cover` plus `cover_checked_at`. "No cover" is rechecked after 30 days.
- **Scope and concurrency:** only the top 50 ranked albums (the largest page), only on a cold search,
  at most 8 checks at once, with a 3 s batch deadline. Unanswered albums stay unknown and are shown.

Measured on the same 30 queries, empty database (`results/2026-10-07-covers.json`):

| | Without cover check | With cover check |
|---|---|---|
| hit@3 (cold = warm) | 28/30 | 28/30 |
| MRR | 0.844 | 0.867 |
| Cover check per cold search | — | median 0.37 s, p90 0.65 s, max 0.88 s |
| Warm search | median 12 ms | median 12 ms (no check) |
| Checked albums without a cover | — | 152 of 750 (20%), now hidden |

No expected album disappeared for lack of a cover. MRR rose because coverless tributes and bootlegs
no longer sit above the expected album. Daft Punk's rank moves between 2 and 3 across runs: Discovery
and Human After All both have 24 releases, so their order follows MusicBrainz's, which isn't fixed.

## Alternatives considered

- **Keep trigram ranking, add a popularity column.** Doesn't fix cause 1: the right albums were never
  fetched.
- **Always search artists first (`/artist` endpoint), then browse their albums.** One more request on
  every search; detecting the artist from the release-group results costs nothing extra.
- **Exclude secondary types only in the query.** Would make "at folsom prison" unfindable.
- **Exclude them only client-side.** With 463 Radiohead release groups and 100 results per request,
  the junk would still use up the slots.

## Consequences

Good:
- 13/30 → 28/30, measured, with a rerunnable script.
- Cached queries are cheap (median 12 ms) and keep MusicBrainz's order.

Bad:
- Cold searches are slower: median 1.9 s instead of 1.0 s, because an artist query costs 2 requests
  (3 for a typo) at ~1 request/second. The rate limit is shared, so busy moments return `partial` sooner.
- Albums that are stored but that MusicBrainz's search doesn't return (e.g. opened by direct link) no
  longer show up in search results while MusicBrainz is reachable.
- The ranking depends on MusicBrainz's scoring, which we don't control; rerun the eval if results drift.
- An artist whose name is a common word ("Blue") takes the top of the results for that word.
