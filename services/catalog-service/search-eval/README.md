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
ADR-010, the run with cover checks, and the raw MusicBrainz diagnosis.

Search hides albums without a cover. When an expected album is missing, `endpoint` asks the Cover
Art Archive about it and prints "no cover: …" in that row and in the summary. Compare a new run with
`eval.py compare results/2026-10-07-adr-010.json after.json`.

The popularity weight (`crate.catalog.search.popularity-weight`) was tuned on this same set, so
scores are optimistic for unseen queries. When adding queries, add them before tuning anything.

This calls the real MusicBrainz API (about 1 request/second), so it is never run by the build.
