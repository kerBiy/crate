# Search evaluation

`queries.json`: 30 realistic queries, each with the album(s) that should be in the top 3,
identified by MusicBrainz release-group MBID. Categories: artist only, artists with many
compilations/live albums, title only, artist + title, short titles, typos, diacritics.

`eval.py` (Python 3 standard library + curl) scores `GET /albums/search`:
hit@3 (the headline score), hit@10 and MRR, overall and per category.

Run it against an **empty** database so results don't depend on what was searched before:

```sh
make infra-up
docker exec crate-postgres-1 psql -U postgres -c "drop database if exists catalog_eval" \
  -c "create database catalog_eval owner catalog_service"
./gradlew :services:catalog-service:bootRun --args='--spring.profiles.active=local --server.port=8092 --spring.datasource.url=jdbc:postgresql://localhost:5432/catalog_eval'

python3 services/catalog-service/search-eval/eval.py endpoint --base http://localhost:8092 --out after.json
python3 services/catalog-service/search-eval/eval.py compare before.json after.json
```

`endpoint` runs the set twice: pass 1 is cold (MusicBrainz is asked), pass 2 is warm (answered
from the DB/cache). `eval.py mb` shows what MusicBrainz itself returns for the query the
*pre-ADR-010* query builder sent (needs `MUSICBRAINZ_CONTACT`); it's kept to reproduce the
original diagnosis, not as a score.

`results/` holds the reports behind docs/adr/010-search-ranking.md: the baseline, the result after
ADR-010, the run with cover checks, and the raw MusicBrainz diagnosis. The `2026-10-11-*` reports
are behind docs/adr/011-deezer-rejected.md; the ListenBrainz ones (`candidates-*`, `popularity-*`,
`lb-baseline-*`, `listenbrainz*`, `step2-*`) are behind docs/adr/012-listenbrainz-popularity.md.

`queries-niche.json`: 9 queries for coverage outside the mainstream (Romanian, Bandcamp-only,
pre-1970), chosen before any tuning or comparison. Report it separately from `queries.json`.

`queries-heldout.json`: 25 queries in the main set's categories, written before any ListenBrainz
data was fetched. Never tune on it; report it next to the main set.

Every report shows hit@1, hit@3, hit@10 and MRR. For artist-name queries (`artist`,
`many-compilations`) it also shows top-5 (share of the expected albums in the top 5) and nDCG@5.

**Offline re-ranking** (no catalog-service needed, results in `results/2026-10-11-listenbrainz.md`):

```sh
python3 eval.py candidates --cases queries.json --parity endpoint-report.json --out cand-main.json
python3 eval.py artist-pages cand-main.json      # complete album lists, for formula G
python3 eval.py popularity cand-main.json --out pop-main.json
python3 eval.py rerank --tune main --set main:cand-main.json:pop-main.json --set heldout:…:… --out rerank.json
```

`candidates` is a Python mirror of the search pipeline (keep it in sync with `MusicBrainzQueryBuilder`,
`MusicBrainzClient` and `SearchRanking`). `--parity` compares it with an `endpoint` report.
`popularity` makes one ListenBrainz batch call per query. `rerank` tunes each formula on the `--tune` set,
then scores every set with the frozen parameters. Cover answers are cached in `results/covers-cache.json`.

**Before/after a ranking change**: run `endpoint` for all three sets on an empty database with the old
code, then again with the new code (empty database again), and compare each pair:

```sh
python3 eval.py endpoint --base http://localhost:8092 --cases queries-heldout.json --out before-heldout.json
# … restart on the new code, empty catalog_eval …
python3 eval.py endpoint --base http://localhost:8092 --cases queries-heldout.json --out after-heldout.json
python3 eval.py compare before-heldout.json after-heldout.json
```

`endpoint` options for other sets and sources:

- `--cases queries-niche.json`: which query file to run (default `queries.json`).
- `--path /albums/search`: the search path on `--base`, to score another endpoint with the same
  response shape (`items[]` with `id`, `title`, `artistCredit`).
- `--match title`: count a hit by normalized title + one shared artist name instead of by MBID,
  for sources without MusicBrainz IDs. Every run prints both matchings; the report stores both
  (`rank_mbid`, `rank_title`).

Search hides albums without a cover. When an expected album is missing, `endpoint` asks the Cover
Art Archive about it and prints "no cover: …" in that row and in the summary. Compare a new run with
`eval.py compare results/2026-10-07-adr-010.json after.json`.

The popularity weight (`crate.catalog.search.popularity-weight`) was tuned on this same set, so
scores are optimistic for unseen queries. When adding queries, add them before tuning anything.

This calls the real MusicBrainz API (about 1 request/second), so it is never run by the build.
