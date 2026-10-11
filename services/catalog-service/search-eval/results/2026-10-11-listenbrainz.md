# ListenBrainz popularity: offline experiment (2026-10-11)

Step 1 of the popularity work: no production code changed. Question: does ListenBrainz's listen data
(same release-group MBIDs, CC0) let MusicBrainz search put the popular album first, like Deezer,
without losing niche albums?

## Method

1. `queries-heldout.json` (25 queries) was written and approved **before** any ListenBrainz data was
   fetched. Expected albums were chosen by what the query most plausibly means.
2. `eval.py candidates`: a Python mirror of today's pipeline (query builder, filters, artist path,
   merge, cover check of the top 50) keeps every candidate with its MusicBrainz score and release
   count (`candidates-*.json`). **Parity** with a fresh endpoint run on an empty DB
   (`lb-baseline-*.json`): the expected album's rank matches on 63/64 queries; the one difference
   ("daft punk", 2 vs 3) is a tie in release count that MusicBrainz orders differently per call.
   Other top-10 differences are ties too, except two where MusicBrainz itself changed between calls
   ("the rolling stones" has 102 albums, and each call returns a different 100 of them; "am" moved
   about 1 score point).
3. `eval.py popularity`: one `POST /1/popularity/release-group` per query with every candidate
   (up to ~190 MBIDs), as a search would make it (`popularity-*.json`).
4. `eval.py rerank`: every formula re-ranks the stored pools offline. Parameters are tuned on the
   **main set only**, chosen by hit@3, then hit@1, then artist nDCG@5, then MRR, smallest parameter
   first. Held-out and niche are then scored once with the frozen parameters
   (`listenbrainz-rerank.json`).

Metrics: hit@1, hit@3, MRR; for artist-name queries (categories `artist`, `many-compilations`)
also **top-5** (share of the expected albums in the top 5) and **nDCG@5** (binary relevance).
u = ListenBrainz listeners (`total_user_count`), L = listens (`total_listen_count`).

| Formula | Ranks the query's results by | Artist path ordered by |
|---|---|---|
| A | today: score + 15·ln(1+releases) | releases |
| B | score + w·ln(1+u) | releases |
| C | score + 15·ln(1+releases) + w·ln(1+u) | releases |
| D | score + w·ln(1+L) | releases |
| E | B | u (token-free batch) |
| E2 | B | L (token-free batch) |
| H | B | ln(1+L) + k·ln(1+releases) (token-free batch) |
| F | E, boost only when score ≥ s | u |
| G | B | L over the artist's **complete** album list: simulates the token-only `top-release-groups-for-artist` |

## Results

**main**

| Formula | Params | hit@1 | hit@3 | MRR | artist top-5 / nDCG@5 |
|---|---|---|---|---|---|
| *endpoint (today, title-matched)* | – | 24/30 | 28/30 | 0.867 | 0.85 / 0.81 |
| *Deezer (ADR-011 run)* | – | 29/30 | 29/30 | 0.971 | 0.93 / 0.92 |
| A | – | 24/30 | 28/30 | 0.861 | 0.82 / 0.78 |
| B | w=8 | 26/30 | 28/30 | 0.894 | 0.82 / 0.78 |
| C | w=2 | 26/30 | 28/30 | 0.894 | 0.82 / 0.78 |
| D | w=5 | 26/30 | 28/30 | 0.894 | 0.82 / 0.78 |
| E | w=8 | 26/30 | 28/30 | 0.900 | 0.83 / 0.82 |
| E2 | w=8 | 27/30 | 28/30 | 0.917 | 0.97 / 0.96 |
| H | w=8, k=0.5 | 27/30 | 28/30 | 0.917 | 1.00 / 0.99 |
| F | w=6, s=50 | 24/30 | 27/30 | 0.850 | 0.83 / 0.82 |
| G | w=8 | 27/30 | 28/30 | 0.917 | 0.97 / 0.96 |

**heldout**

| Formula | Params | hit@1 | hit@3 | MRR | artist top-5 / nDCG@5 |
|---|---|---|---|---|---|
| *endpoint (today, title-matched)* | – | 19/25 | 24/25 | 0.863 | 1.00 / 0.91 |
| A | – | 19/25 | 24/25 | 0.863 | 1.00 / 0.91 |
| B | w=8 | 20/25 | 25/25 | 0.887 | 1.00 / 0.91 |
| C | w=2 | 22/25 | 25/25 | 0.933 | 1.00 / 0.91 |
| D | w=5 | 20/25 | 24/25 | 0.883 | 1.00 / 0.91 |
| E | w=8 | 17/25 | 23/25 | 0.797 | 0.61 / 0.52 |
| E2 | w=8 | 18/25 | 24/25 | 0.830 | 0.78 / 0.67 |
| H | w=8, k=0.5 | 18/25 | 24/25 | 0.837 | 1.00 / 0.80 |
| F | w=6, s=50 | 15/25 | 21/25 | 0.744 | 0.61 / 0.52 |
| G | w=8 | 18/25 | 24/25 | 0.830 | 0.78 / 0.67 |

**niche**

| Formula | Params | hit@1 | hit@3 | MRR | artist top-5 / nDCG@5 |
|---|---|---|---|---|---|
| *endpoint (today, title-matched)* | – | 8/9 | 9/9 | 0.944 | – |
| *Deezer (ADR-011 run)* | – | 5/9 | 5/9 | 0.567 | – |
| A | – | 8/9 | 9/9 | 0.944 | – |
| B | w=8 | 8/9 | 9/9 | 0.944 | – |
| C | w=2 | 8/9 | 9/9 | 0.944 | – |
| D | w=5 | 8/9 | 9/9 | 0.944 | – |
| E | w=8 | 8/9 | 9/9 | 0.944 | – |
| E2 | w=8 | 8/9 | 9/9 | 0.944 | – |
| H | w=8, k=0.5 | 8/9 | 9/9 | 0.944 | – |
| F | w=6, s=50 | 8/9 | 9/9 | 0.944 | – |
| G | w=8 | 8/9 | 9/9 | 0.944 | – |

**Per category, A (today) → C**

| Set | Category | hit@1 | hit@3 | MRR |
|---|---|---|---|---|
| main | artist | 3 → 3 /4 | 4 → 4 /4 | 0.83 → 0.83 |
| main | many-compilations | 4 → 4 /4 | 4 → 4 /4 | 1.00 → 1.00 |
| main | title | 5 → 5 /6 | 6 → 6 /6 | 0.92 → 0.92 |
| main | artist+title | 4 → 4 /4 | 4 → 4 /4 | 1.00 → 1.00 |
| main | short-title | 1 → 2 /4 | 2 → 2 /4 | 0.38 → 0.50 |
| main | typo | 3 → 4 /4 | 4 → 4 /4 | 0.88 → 1.00 |
| main | diacritics | 4 → 4 /4 | 4 → 4 /4 | 1.00 → 1.00 |
| heldout | artist | 2 → 2 /3 | 3 → 3 /3 | 0.83 → 0.83 |
| heldout | many-compilations | 3 → 3 /3 | 3 → 3 /3 | 1.00 → 1.00 |
| heldout | title | 3 → 3 /4 | 4 → 4 /4 | 0.83 → 0.83 |
| heldout | artist+title | 4 → 4 /4 | 4 → 4 /4 | 1.00 → 1.00 |
| heldout | short-title | 0 → 3 /4 | 3 → 4 /4 | 0.44 → 0.88 |
| heldout | typo | 4 → 4 /4 | 4 → 4 /4 | 1.00 → 1.00 |
| heldout | diacritics | 3 → 3 /3 | 3 → 3 /3 | 1.00 → 1.00 |
| niche | romanian | 3 → 3 /3 | 3 → 3 /3 | 1.00 → 1.00 |
| niche | bandcamp | 3 → 3 /3 | 3 → 3 /3 | 1.00 → 1.00 |
| niche | pre-1970 | 2 → 2 /3 | 3 → 3 /3 | 0.83 → 0.83 |

Today's endpoint is title-matched here, like Deezer, so its row can differ slightly from A (ties).
Deezer has no held-out run.

## Findings

1. **C (w = 2) is the only formula that improves without hurting any set.**
   - main: hit@1 24 → 26, MRR 0.861 → 0.894 ("21" and "radiohed" move to #1).
   - held-out: hit@1 19 → 22, hit@3 24 → 25, MRR 0.863 → 0.933. All short titles improve: "am"
     4 → 1, "25" 2 → 1, "currents" 2 → 1.
   - niche: unchanged (9/9).
   - Choosing C needs no held-out data. B, C and D tie on main. On the main grid, though, C is flat
     from w = 2 to w = 10+ (and w = 0 is exactly today's ranking), while B and D lose hit@3 below
     w = 6 and w = 5. On held-out and niche, C scores the same for every w from 1 to 8.
2. **Popularity in the artist path helps main but hurts held-out, so none of E, E2, H, G qualifies.**
   - main artist nDCG@5: 0.78 → 0.96–0.99 (The Beatles 0.49 → 1.00, Daft Punk 0.62 → 0.97).
   - held-out artist nDCG@5: 0.91 → 0.67 (E2/G) or 0.80 (H). Elvis Presley, Frank Sinatra and The
     Rolling Stones get worse.
   - Cause: older artists' most-played "albums" are greatest-hits sets MusicBrainz doesn't flag
     as compilations (Gold Collection Vol. 1, A Man and His Music) and Christmas albums.
   - **Listeners are worse than listens here**: E puts Black Panther: The Album first for Kendrick
     Lamar, because many people played one track from it.
   - k is not a fix: H with k = 0.5 (tuned on main) still loses on held-out.
3. **The token-only endpoint adds nothing: G = E2 on every metric of every set.** It ranks by the
   same listen counts the token-free batch endpoint returns. The only extra it would bring is
   albums past MusicBrainz's first 100, which mattered for one artist (The Rolling Stones, 102), and
   not in the top 5.
4. **"blue" and "red" are a recall problem, not a ranking problem.** Joni Mitchell's *Blue* and both
   *Red*s are not among MusicBrainz's 100 results for those words, so no re-ranking can find them.
   The sitewide ListenBrainz top list has Taylor Swift's *Red* at #314 but not *Blue*. Only a local
   pool of popular albums (Step 2's pre-warming) could fix these, and only for albums in it.
5. **ListenBrainz coverage is good**: every expected album that was in a pool has ListenBrainz data
   (main 51/51, held-out 33/33, niche 9/9; the least-heard niche album has 4 listeners). About 80%
   of all candidates have counts; the rest count as 0, which is today's behaviour for them.
6. **Latency**: one batch call per search took median 185–200 ms, max 345 ms (all three sets).
   A 300–400 ms timeout with today's ranking as the fallback fits.

## Rate limits and terms

| What | Finding | Affects us |
|---|---|---|
| Rate limit | Docs: never more than one call per second per client; 429 when exceeded; `X-RateLimit-*` headers. Measured: `X-RateLimit-Limit: 30` per ~10 s window. ([API docs](https://listenbrainz.readthedocs.io/en/latest/users/api/index.html)) | One batch per cold search, which already waits on MusicBrainz's ~1/s; pre-warming must pace itself |
| Batch endpoint | `POST /1/popularity/release-group`, up to 1000 MBIDs (`MAX_ITEMS_PER_GET`), no token, nulls for unknown MBIDs ([docs](https://listenbrainz.readthedocs.io/en/latest/users/api/popularity.html), [source](https://github.com/metabrainz/listenbrainz-server/blob/master/listenbrainz/webserver/views/api_tools.py)) | Fits one call per search |
| Artist top lists | `top-release-groups-for-artist` returns **401 without a user token** ("bad actors and AI scrapers"); public responses are CDN-cached for 10 min, so a cached artist can look token-free ([source](https://github.com/metabrainz/listenbrainz-server/blob/master/listenbrainz/webserver/views/popularity_api.py)) | Needs a ListenBrainz account token as a secret. Not needed: finding 3 |
| Sitewide top list | `GET /1/stats/sitewide/release-groups`, no token, but **only the top 1000** per range (offset 1000 returns nothing for `all_time`, `year`, `month`, `week`) ([docs](https://listenbrainz.readthedocs.io/en/latest/users/api/statistics.html)) | Pre-warming seeds at most ~1000 albums per range, a few thousand in total |
| Data license | "All of our data is available for commercial use" ([listenbrainz-server README](https://github.com/metabrainz/listenbrainz-server)); MetaBrainz: "factual database content will be in the public domain" ([social contract](https://metabrainz.org/social-contract)). Listen counts are factual. Secondary sources say CC0; I found no primary ListenBrainz page that states it. *Later the same day:* [metabrainz.org/datasets/postgres-dumps](https://metabrainz.org/datasets/postgres-dumps) lists the ListenBrainz data dumps as CC0; see ADR-012 for what that does and doesn't cover | Storing counts in our DB looks fine; credit ListenBrainz anyway |
| Commercial use | Free for non-commercial, personal, open-source and research use; companies with a revenue stream are asked to pay a supporter tier from $100/month ([tiers](https://metabrainz.org/supporters/account-type), [social contract](https://metabrainz.org/social-contract)) | Free while Crate is a non-commercial portfolio project; revisit if it ever makes money |
| User-Agent | Required with contact info for MusicBrainz ([rate limiting](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)); ListenBrainz docs ask for the same | Reuse the `MUSICBRAINZ_CONTACT` User-Agent |

## Recommendation for Step 2

- Search ranking: **C, w = 2**: `score + 15·ln(1+releases) + 2·ln(1+listeners)`. With no counts
  (ListenBrainz down or slow) it is exactly today's ranking, so the fallback is free.
- Artist path: **keep ordering by release count**. Popularity there helps main and hurts held-out
  (finding 2), so it fails the "held-out must not get worse" rule. Neither the token-free batch nor
  the token endpoint qualifies; the token endpoint would add nothing anyway (finding 3).
- Pre-warming: worth it for speed, but it covers at most a few thousand albums (finding 4 and the
  1000-row cap). Whether it adds recall for "blue" and "red" depends on searching that local pool,
  which is a design question for Step 2.
