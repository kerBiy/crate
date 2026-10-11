#!/usr/bin/env python3
"""
Search quality evaluation for GET /albums/search (see README.md next to this file).

A case passes when any of its expected release groups (by MusicBrainz MBID) is in the top 3.
Reported: hit@3 (the score), hit@10, MRR (mean of 1/rank of the best expected album, 0 if missing).

  eval.py endpoint [--base http://localhost:8082] [--passes 2] [--out report.json]
      Calls catalog-service directly. Pass 1 is "cold" if search_cache was cleared first; pass 2
      repeats the same queries ("warm": answered from the DB / cache).
  eval.py mb [--out report.json]
      Diagnostic: what MusicBrainz itself returns for the query the pre-ADR-010 builder sent
      (limit 25), before our filtering and ranking. Needs MUSICBRAINZ_CONTACT.
  eval.py compare before.json after.json
      Per-query and summary diff of two reports.

Python standard library only. HTTP goes through curl: python.org builds on macOS often lack CA
certificates, and curl uses the system's.
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys
import time
import unicodedata
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlencode

HERE = Path(__file__).resolve().parent
TOP = 3
MB_BASE = "https://musicbrainz.org/ws/2"
MB_INTERVAL = 1.1  # MusicBrainz allows ~1 request/second per IP
MB_LIMIT = 25      # crate.catalog.search.musicbrainz-limit


# ---------------------------------------------------------------- HTTP

def http_get_json(url, user_agent=None, timeout=30):
    args = ["curl", "-sS", "--max-time", str(timeout), "-H", "Accept: application/json",
            "-w", "\n%{http_code}"]
    if user_agent:
        args += ["-A", user_agent]
    p = subprocess.run(args + [url], capture_output=True, text=True)
    if p.returncode != 0:
        return None, f"curl exit {p.returncode}: {p.stderr.strip()}"
    body, _, code = p.stdout.rpartition("\n")
    return (json.loads(body) if code == "200" else None), int(code)


_last_mb_call = 0.0


def mb_get(path, params):
    global _last_mb_call
    contact = os.environ.get("MUSICBRAINZ_CONTACT")
    if not contact:
        sys.exit("Set MUSICBRAINZ_CONTACT (MusicBrainz blocks clients without a contact in the User-Agent)")
    url = f"{MB_BASE}/{path}?" + urlencode({**params, "fmt": "json"})
    for attempt in range(1, 6):
        wait = _last_mb_call + MB_INTERVAL - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        _last_mb_call = time.monotonic()
        body, status = http_get_json(url, user_agent=f"Crate-eval/0.1 ( {contact} )")
        if body is not None:
            return body
        if status != 503 and not isinstance(status, str):
            sys.exit(f"MusicBrainz answered {status} for {url}")
        time.sleep(2 * attempt)  # 503 = rate limited; back off
    sys.exit(f"MusicBrainz kept failing for {url}")


_cover_cache = {}


def has_cover(mbid):
    """The check catalog-service does: HEAD front-250 on the Cover Art Archive, 307 = yes, 404 = no."""
    if mbid not in _cover_cache:
        p = subprocess.run(["curl", "-sS", "-o", "/dev/null", "-I", "--max-time", "10", "-w", "%{http_code}",
                            f"https://coverartarchive.org/release-group/{mbid}/front-250"],
                           capture_output=True, text=True)
        status = p.stdout.strip()
        _cover_cache[mbid] = False if status == "404" else True if status.startswith(("2", "3")) else None
    return _cover_cache[mbid]


# ---------------------------------------------------------------- the pre-ADR-010 query builder (mirror)

SEPARATOR = " - "
SPECIAL = set('+-&|!(){}[]^"~*?:\\/')
DASHES = re.compile("[‐-―−]")


def current_mb_query(user_input):
    """Mirrors MusicBrainzQueryBuilder.build before ADR-010 (commit baafba7)."""
    q = unicodedata.normalize("NFKC", user_input)
    q = re.sub(r"\s+", " ", DASHES.sub("-", q)).strip()

    def escape(text):
        return " ".join(w.lower() if w in ("AND", "OR", "NOT")
                        else "".join("\\" + c if c in SPECIAL else c for c in w)
                        for w in text.split(" "))

    split = q.find(SEPARATOR)
    if 0 < split and split + len(SEPARATOR) < len(q):
        artist, title = q[:split], q[split + len(SEPARATOR):]
        return f"artist:({escape(artist)}) AND releasegroup:({escape(title)}) AND primarytype:(album OR ep)"
    t = escape(q)
    return f"(releasegroup:({t}) OR artist:({t})) AND primarytype:(album OR ep)"


# ---------------------------------------------------------------- scoring

def load_cases(name="queries.json"):
    return json.loads((HERE / name).read_text())["cases"]


# Matching by title + artist, for sources without MBIDs (--match title).
FOLD = str.maketrans({"æ": "ae", "Æ": "AE", "ø": "o", "Ø": "O", "ß": "ss", "ð": "d", "þ": "th", "ł": "l"})


def norm(text):
    text = unicodedata.normalize("NFKD", (text or "").translate(FOLD))
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    text = re.sub(r"[^\w\s]|_", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def artist_names(credit):
    """'Jay‐Z & Kanye West' → {'jay z', 'kanye west'}: each credited name, normalized, no leading 'the'."""
    parts = re.split(r"\s*(?:&|,|•|/|\+|;| and | feat\.? | featuring | with | x )\s*", credit or "", flags=re.I)
    return {re.sub(r"^the ", "", norm(p)) for p in parts if norm(p)}


def same_artist(expected, credit):
    return bool(artist_names(expected) & artist_names(credit))


def best_rank_by_title(items, expected):
    for i, item in enumerate(items, 1):
        if any(norm(item["title"]) == norm(e["title"]) and same_artist(e["artist"], item["artistCredit"])
               for e in expected):
            return i
    return None


def best_rank(ids, expected):
    wanted = {e["mbid"] for e in expected}
    for i, item_id in enumerate(ids, 1):
        if item_id in wanted:
            return i
    return None


ARTIST_CATEGORIES = ("artist", "many-compilations")  # the whole query is an artist's name
ORDER_DEPTH = 5


def expected_positions(ids, expected):
    """1-based position of each expected album in ids, None when missing."""
    pos = {item_id: i for i, item_id in enumerate(ids, 1)}
    return [pos.get(e["mbid"]) for e in expected]


def expected_positions_by_title(items, expected):
    return [next((i for i, item in enumerate(items, 1) if norm(item["title"]) == norm(e["title"])
                  and same_artist(e["artist"], item["artistCredit"])), None) for e in expected]


def ordering(positions):
    """For artist queries: share of the expected albums in the top 5, and binary nDCG@5."""
    found = [p for p in positions if p and p <= ORDER_DEPTH]
    ideal = min(ORDER_DEPTH, len(positions))
    dcg = sum(1 / math.log2(p + 1) for p in found)
    idcg = sum(1 / math.log2(i + 1) for i in range(1, ideal + 1))
    return len(found) / ideal, dcg / idcg


def summarize(results):
    def stats(rs):
        n = len(rs)
        ranks = [r["rank"] for r in rs]
        out = {
            "n": n,
            "hit@1": sum(1 for r in ranks if r == 1),
            f"hit@{TOP}": sum(1 for r in ranks if r and r <= TOP),
            "hit@10": sum(1 for r in ranks if r and r <= 10),
            "mrr": round(sum(1 / r for r in ranks if r) / n, 3) if n else 0,
        }
        artist = [ordering(r["positions"]) for r in rs if r["category"] in ARTIST_CATEGORIES and r.get("positions")]
        if artist:
            out["artist_n"] = len(artist)
            out[f"top{ORDER_DEPTH}"] = round(sum(a[0] for a in artist) / len(artist), 3)
            out[f"ndcg@{ORDER_DEPTH}"] = round(sum(a[1] for a in artist) / len(artist), 3)
        return out
    by_cat = defaultdict(list)
    for r in results:
        by_cat[r["category"]].append(r)
    return {"all": stats(results), "by_category": {c: stats(rs) for c, rs in by_cat.items()}}


def describe_stats(st):
    text = f"hit@1 {st['hit@1']}/{st['n']}  hit@{TOP} {st[f'hit@{TOP}']}/{st['n']}  hit@10 {st['hit@10']}/{st['n']}  MRR {st['mrr']}"
    if "artist_n" in st:
        text += f"  artist top{ORDER_DEPTH} {st[f'top{ORDER_DEPTH}']} nDCG@{ORDER_DEPTH} {st[f'ndcg@{ORDER_DEPTH}']}"
    return text


def print_results(title, results):
    print(f"\n=== {title}")
    print(f"{'query':28} {'category':18} {'rank':>5}  top {TOP}")
    for r in results:
        rank = r["rank"] if r["rank"] else "-"
        mark = "ok " if r["rank"] and r["rank"] <= TOP else "XX "
        top = " | ".join(r["top"][:TOP]) or "(no results)"
        extra = f"  [{r['note']}]" if r.get("note") else ""
        print(f"{mark}{r['query'][:25]:25} {r['category']:18} {rank:>5}  {top}{extra}")
    s = summarize(results)
    print(f"\nscore: {describe_stats(s['all'])}")
    for c, st in s["by_category"].items():
        print(f"  {c:18} {describe_stats(st)}")
    return s


# ---------------------------------------------------------------- modes

def run_endpoint(args):
    cases = load_cases(args.cases)
    passes = []
    for p in range(1, args.passes + 1):
        results = []
        for c in cases:
            url = f"{args.base}{args.path}?" + urlencode({"q": c["query"], "limit": 20})
            start = time.monotonic()
            body, status = http_get_json(url)
            ms = round((time.monotonic() - start) * 1000)
            if body is None:
                sys.exit(f"{url} failed: {status}")
            items = body["items"]
            rank_mbid = best_rank([i["id"] for i in items], c["expected"])
            rank_title = best_rank_by_title(items, c["expected"])
            rank = rank_title if args.match == "title" else rank_mbid
            positions = (expected_positions_by_title(items, c["expected"]) if args.match == "title"
                         else expected_positions([i["id"] for i in items], c["expected"]))
            # Search hides albums without a cover: say so when that's why an expected one is missing.
            returned = {i["id"] for i in items}
            no_cover = [] if rank and rank <= TOP else [
                f"{e['title']} — {e['artist']}" for e in c["expected"]
                if e["mbid"] not in returned and has_cover(e["mbid"]) is False]
            notes = (["partial"] if body.get("partial") else []) + [f"no cover: {t}" for t in no_cover]
            results.append({
                "query": c["query"], "category": c["category"], "rank": rank, "ms": ms,
                "rank_mbid": rank_mbid, "rank_title": rank_title, "positions": positions,
                "partial": body.get("partial", False),
                "top": [f"{i['title']} — {i['artistCredit']}" for i in items[:10]],
                "no_cover": no_cover,
                "note": "; ".join(notes),
            })
        label = "cold" if p == 1 else "warm" if p == 2 else f"pass {p}"
        summary = print_results(f"endpoint {args.base}, pass {p} ({label})", results)
        ms = sorted(r["ms"] for r in results)
        print(f"  latency ms: median {ms[len(ms) // 2]}, max {ms[-1]}")
        other = "mbid" if args.match == "title" else "title"
        alt = summarize([{**r, "rank": r[f"rank_{other}"]} for r in results])["all"]
        print(f"  same run matched by {other} (positions not recomputed): hit@{TOP} {alt[f'hit@{TOP}']}/{alt['n']}"
              f"  hit@10 {alt['hit@10']}/{alt['n']}  MRR {alt['mrr']}")
        dropped = [(r["query"], t) for r in results for t in r["no_cover"]]
        print("  expected albums missing because they have no cover: "
              + (", ".join(f"{t} ('{q}')" for q, t in dropped) if dropped else "none"))
        passes.append({"label": label, "results": results, "summary": summary})
    write(args.out, {"mode": "endpoint", "base": args.base, "path": args.path, "match": args.match,
                     "cases": args.cases, "passes": passes})


def run_mb(args):
    results = []
    for c in load_cases():
        query = current_mb_query(c["query"])
        body = mb_get("release-group", {"query": query, "limit": MB_LIMIT})
        groups = body.get("release-groups", [])
        kept = [g for g in groups if g.get("primary-type") in ("Album", "EP")]
        rank = best_rank([g["id"] for g in kept], c["expected"])
        secondary = sum(1 for g in kept[:10] if g.get("secondary-types"))
        results.append({
            "query": c["query"], "category": c["category"], "rank": rank, "mb_query": query,
            "mb_total": body.get("count"),
            "top": [describe(g) for g in kept[:10]],
            "groups": [{"id": g["id"], "title": g["title"], "score": g.get("score"),
                        "count": g.get("count"), "secondary": g.get("secondary-types", [])}
                       for g in kept],
            "note": f"total {body.get('count')}, {secondary}/10 of top have secondary types",
        })
    summary = print_results(f"MusicBrainz raw, current query, limit {MB_LIMIT}", results)
    write(args.out, {"mode": "mb", "passes": [{"label": "mb", "results": results, "summary": summary}]})


def describe(g):
    artist = "".join(c["name"] + c.get("joinphrase", "") for c in g.get("artist-credit", []))
    sec = "/".join(g.get("secondary-types", []))
    return f"{g['title']} — {artist} ({g.get('primary-type')}{'/' + sec if sec else ''}, n={g.get('count')})"


# ---------------------------------------------------------------- mirror of today's pipeline (ADR-010)
# A Python copy of MusicBrainzQueryBuilder.build, MusicBrainzClient's filters and SearchRanking, used
# to keep every candidate with its MusicBrainz score, so rankings can be recomputed offline.
# `candidates` checks it against the endpoint (parity); keep it in sync with the Java code.

EXCLUDED_SECONDARY_TYPES = ["Compilation", "Live", "Remix", "DJ-mix", "Mixtape/Street", "Demo", "Interview",
                            "Audiobook", "Audio drama"]
EXCLUDED = "secondarytype:(" + " OR ".join(f'"{t.lower()}"' for t in EXCLUDED_SECONDARY_TYPES) + ")"
ALBUMS_AND_EPS = "primarytype:(album OR ep)"
POOL_LIMIT = 100           # crate.catalog.search.musicbrainz-limit
POPULARITY_WEIGHT = 15.0   # crate.catalog.search.popularity-weight
COVER_CHECK_COUNT = 50     # crate.catalog.search.cover-check-count
PAGE = 20                  # what eval.py endpoint asks for
JAVA_WS = re.compile(r"\s+", re.ASCII)  # Java's \s is ASCII-only


def search_text(text):
    """SearchText.normalize: NFKC, strip accents, lowercase, collapse whitespace."""
    text = unicodedata.normalize("NFD", unicodedata.normalize("NFKC", text or ""))
    text = "".join(c for c in text if not unicodedata.category(c).startswith("M"))
    return JAVA_WS.sub(" ", text.lower()).strip()


def builder_normalize(text):
    return JAVA_WS.sub(" ", DASHES.sub("-", unicodedata.normalize("NFKC", text or ""))).strip()


def _escape_word(word):
    if word in ("AND", "OR", "NOT"):
        return word.lower()
    return "".join("\\" + c if c in SPECIAL else c for c in word)


def _words(text):
    return [w for w in text.split(" ") if re.search(r"[^\W_]", w)]


def _fuzzy(term, word, fuzzy):
    return term + "~" if fuzzy and len(word) >= 4 else term


def build_query(user_input, fuzzy):
    """MusicBrainzQueryBuilder.build → (lucene, title)."""
    q = builder_normalize(user_input)

    def all_of(text):
        terms = " AND ".join(_fuzzy(_escape_word(w), w, fuzzy) for w in _words(text))
        return terms or " ".join(_escape_word(w) for w in text.split(" "))

    def excluding(terms, phrase):
        required = f"({terms}) AND {ALBUMS_AND_EPS}"
        quoted = phrase.replace("\\", "\\\\").replace('"', '\\"')
        return f'({required} AND NOT {EXCLUDED}) OR ({required} AND releasegroup:"{quoted}")'

    split = q.find(SEPARATOR)
    if 0 < split and split + len(SEPARATOR) < len(q):
        artist, title = q[:split], q[split + len(SEPARATOR):]
        return excluding(f"artist:({all_of(artist)}) AND releasegroup:({all_of(title)})", title), title
    terms = " AND ".join(f"(releasegroup:({t}) OR artist:({t}))"
                         for t in (_fuzzy(_escape_word(w), w, fuzzy) for w in _words(q)))
    if not terms:
        terms = "releasegroup:(" + " ".join(_escape_word(w) for w in q.split(" ")) + ")"
    return excluding(terms, q), q


def _is_excluded(g):
    return any(t in EXCLUDED_SECONDARY_TYPES for t in g.get("secondary-types") or [])


def _album(g, score=None):
    credits = g.get("artist-credit") or []
    return {"id": g["id"], "title": g["title"],
            "credit": "".join(c["name"] + (c.get("joinphrase") or "") for c in credits),
            "artists": [{"id": c["artist"].get("id"), "name": c["artist"].get("name")} for c in credits],
            "releases": g.get("count") or 0, "score": score}


def mb_search_hits(user_input, fuzzy):
    lucene, title = build_query(user_input, fuzzy)
    exact = search_text(title)
    groups = mb_get("release-group", {"query": lucene, "limit": POOL_LIMIT}).get("release-groups", [])
    return [_album(g, g.get("score") or 0) for g in groups
            if g.get("primary-type") in ("Album", "EP") and (not _is_excluded(g) or search_text(g["title"]) == exact)]


def mb_artist_albums(artist_id):
    lucene = f"arid:{artist_id} AND primarytype:album AND NOT {EXCLUDED}"
    groups = mb_get("release-group", {"query": lucene, "limit": POOL_LIMIT}).get("release-groups", [])
    return [_album(g) for g in groups if g.get("primary-type") in ("Album", "EP") and not _is_excluded(g)]


def _without_article(s):
    return s[4:] if s.startswith("the ") else s


def matching_artist(normalized, hits):
    """SearchRanking.matchingArtist."""
    wanted = _without_article(normalized)
    for h in hits:
        for a in h["artists"]:
            if not a["id"]:
                continue
            names = [a["name"]] + ([h["credit"]] if len(h["artists"]) == 1 else [])
            if any(_without_article(search_text(n)) == wanted for n in names):
                return a["id"]
    return None


def merge(artist_albums, hits, artist_key, hit_key):
    """SearchRanking.merge with pluggable sort keys (higher first; Python's sort is stable, like Java's)."""
    ranked = {}
    for a in sorted(artist_albums, key=lambda a: -artist_key(a)) + sorted(hits, key=lambda h: -hit_key(h)):
        ranked.setdefault(a["id"], a)
    return list(ranked.values())


def current_artist_key(a):
    return a["releases"]


def current_hit_key(h, weight=POPULARITY_WEIGHT):
    return h["score"] + weight * math.log1p(h["releases"])


def page(ranked, covers, check=True):
    """What GET /albums/search returns: covers checked for the top 50 only, has_cover=false hidden."""
    if check:
        for a in ranked[:COVER_CHECK_COUNT]:
            if a["id"] not in covers:
                covers[a["id"]] = has_cover(a["id"])
    hidden = {a["id"] for a in ranked[:COVER_CHECK_COUNT] if covers.get(a["id"]) is False}
    return [a for a in ranked if a["id"] not in hidden][:PAGE]


COVERS_FILE = HERE / "results" / "covers-cache.json"


def load_covers():
    if COVERS_FILE.exists():
        _cover_cache.update(json.loads(COVERS_FILE.read_text()))
    return _cover_cache


def save_covers():
    COVERS_FILE.write_text(json.dumps(_cover_cache, indent=0, sort_keys=True))


def run_candidates(args):
    """Fetch each query's candidate pool from MusicBrainz; check parity against an endpoint report."""
    covers = load_covers()
    endpoint = None
    if args.parity:
        # Per query, a pass MusicBrainz answered: a cold pass that fell back (partial) is re-asked when warm.
        endpoint = {}
        for p in json.loads(Path(args.parity).read_text())["passes"]:
            for r in p["results"]:
                if r["query"] not in endpoint or endpoint[r["query"]]["partial"]:
                    endpoint[r["query"]] = r
    pools, mismatches = [], 0
    for c in load_cases(args.cases):
        normalized = search_text(c["query"])
        hits, fuzzy = mb_search_hits(c["query"], False), False
        if not hits:
            hits, fuzzy = mb_search_hits(c["query"], True), True
        artist = matching_artist(normalized, hits)
        artist_albums = mb_artist_albums(artist) if artist else []
        top = page(merge(artist_albums, hits, current_artist_key, current_hit_key), covers)
        rank = best_rank([a["id"] for a in top], c["expected"])
        line = f"{c['query'][:28]:28} rank {rank or '-':>2}  pool {len(hits)}+{len(artist_albums)}"
        if endpoint:
            want = [t.split(" — ")[0] for t in endpoint[c["query"]]["top"]]
            got = [a["title"] for a in top[:len(want)]]
            same = want == got
            mismatches += not same
            line += "  parity ok" if same else f"  PARITY MISMATCH\n    endpoint {want}\n    mirror   {got}"
        print(line)
        pools.append({"query": c["query"], "category": c["category"], "expected": c["expected"],
                      "fuzzy": fuzzy, "artist_id": artist, "hits": hits, "artist_albums": artist_albums})
    save_covers()
    if endpoint:
        print(f"\nparity: {len(pools) - mismatches}/{len(pools)} queries identical (top 10 titles)")
    write(args.out, {"mode": "candidates", "cases": args.cases, "pools": pools})


# ---------------------------------------------------------------- ListenBrainz popularity (offline re-ranking)

LB_POPULARITY = "https://api.listenbrainz.org/1/popularity/release-group"
LB_MAX_ITEMS = 1000  # MAX_ITEMS_PER_GET in listenbrainz-server's api_tools.py
LB_INTERVAL = 1.0    # the API docs ask for at most one call per second per client


def lb_popularity(mbids):
    """One batch call, as a search would make it: {mbid: (listens, listeners)} and the time it took."""
    body = json.dumps({"release_group_mbids": mbids})
    for attempt in range(1, 6):
        start = time.monotonic()
        p = subprocess.run(["curl", "-sS", "--max-time", "30", "-X", "POST", "-H", "Content-Type: application/json",
                            "-A", "Crate-eval/0.1", "-D", "-", "-w", "\n%{http_code}", "--data-binary", "@-",
                            LB_POPULARITY], input=body, capture_output=True, text=True)
        ms = round((time.monotonic() - start) * 1000)
        out, _, code = p.stdout.rpartition("\n")
        if code == "200":
            # Text mode turns \r\n into \n; the body follows the last header block.
            payload = json.loads(re.split(r"^HTTP/\S+ \d+.*?\n\n", out, flags=re.M | re.S)[-1])
            return {r["release_group_mbid"]: (r["total_listen_count"], r["total_user_count"]) for r in payload}, ms
        reset = re.search(r"x-ratelimit-reset-in: (\d+)", out, re.I)
        time.sleep(int(reset.group(1)) + 1 if code == "429" and reset else 2 * attempt)
    sys.exit(f"ListenBrainz kept failing ({code})")


def run_popularity(args):
    """Listen and listener counts for every candidate of every query: one batch call per query."""
    pools = json.loads(Path(args.candidates).read_text())["pools"]
    counts, latencies = {}, []
    for pool in pools:
        ids = list(dict.fromkeys(a["id"] for a in pool["hits"] + pool["artist_albums"]
                                    + pool.get("artist_albums_all", [])))
        for i in range(0, len(ids), LB_MAX_ITEMS):
            got, ms = lb_popularity(ids[i:i + LB_MAX_ITEMS])
            counts.update(got)
            latencies.append(ms)
            time.sleep(LB_INTERVAL)
        print(f"{pool['query'][:28]:28} {len(ids):4} albums  {latencies[-1]} ms")
    lat = sorted(latencies)
    print(f"latency ms: median {lat[len(lat) // 2]}, p90 {lat[int(len(lat) * 0.9)]}, max {lat[-1]}")
    write(args.out, {"mode": "popularity", "candidates": args.candidates, "latency_ms": latencies, "counts": counts})


def _ln(x):
    return math.log1p(x or 0)


def formulas(counts):
    """name → (description, parameter grid, factory(params) → (artist_key, hit_key, artist list)).
    Higher key first. u = listeners, L = listens; missing counts are 0."""
    users = lambda a: (counts.get(a["id"]) or (0, 0))[1] or 0
    listens = lambda a: (counts.get(a["id"]) or (0, 0))[0] or 0
    ws = [0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40]
    by_releases = current_artist_key
    b = lambda p: lambda h: h["score"] + p["w"] * _ln(users(h))
    return {
        "A": ("today: score + 15·ln(1+releases); artist path by releases", [{}],
              lambda p: (by_releases, current_hit_key, "artist_albums")),
        "B": ("score + w·ln(1+u); artist path by releases", [{"w": w} for w in ws],
              lambda p: (by_releases, b(p), "artist_albums")),
        "C": ("score + 15·ln(1+releases) + w·ln(1+u); artist path by releases", [{"w": w} for w in ws],
              lambda p: (by_releases, lambda h: current_hit_key(h) + p["w"] * _ln(users(h)), "artist_albums")),
        "D": ("score + w·ln(1+L); artist path by releases", [{"w": w} for w in ws],
              lambda p: (by_releases, lambda h: h["score"] + p["w"] * _ln(listens(h)), "artist_albums")),
        "E": ("B; artist path by u (token-free: batch counts for the albums MusicBrainz returned)",
              [{"w": w} for w in ws], lambda p: (users, b(p), "artist_albums")),
        "E2": ("B; artist path by L (token-free: batch counts for the albums MusicBrainz returned)",
               [{"w": w} for w in ws], lambda p: (listens, b(p), "artist_albums")),
        "H": ("B; artist path by ln(1+L) + k·ln(1+releases) (token-free)",
              [{"w": w, "k": k} for w in ws for k in (0, 0.5, 1, 2, 3, 5, 8)],
              lambda p: (lambda a: _ln(listens(a)) + p["k"] * _ln(a["releases"]), b(p), "artist_albums")),
        "F": ("E; boost only when score ≥ s", [{"w": w, "s": s} for w in ws for s in (50, 70, 80, 90)],
              lambda p: (users, lambda h: h["score"] + (p["w"] * _ln(users(h)) if h["score"] >= p["s"] else 0),
                         "artist_albums")),
        "G": ("B; artist path by L over the artist's complete album list (simulates the token-only "
              "top-release-groups-for-artist)", [{"w": w} for w in ws],
              lambda p: (listens, b(p), "artist_albums_all")),
    }


def rerank(pools, keys, covers):
    artist_key, hit_key, artist_field = keys
    results = []
    for pool in pools:
        artist_albums = pool.get(artist_field, pool["artist_albums"]) if pool["artist_albums"] else []
        top = page(merge(artist_albums, pool["hits"], artist_key, hit_key), covers)
        ids = [a["id"] for a in top]
        results.append({"query": pool["query"], "category": pool["category"],
                        "rank": best_rank(ids, pool["expected"]),
                        "positions": expected_positions(ids, pool["expected"]),
                        "top": [f"{a['title']} — {a['credit']}" for a in top[:10]]})
    return results


def selection_key(summary):
    """How a formula's parameters are chosen: no top-3 losses first, then the most popular album
    first (hit@1), then the artist queries' order (nDCG@5), then MRR."""
    a = summary["all"]
    return a[f"hit@{TOP}"], a["hit@1"], a.get(f"ndcg@{ORDER_DEPTH}", 0), a["mrr"]


def reference_results(path, cases_by_query):
    """An endpoint report rescored by title + artist (works for Deezer, which has no MBIDs)."""
    passes = json.loads(Path(path).read_text())["passes"]
    chosen = {}
    for p in passes:  # per query, a pass that MusicBrainz answered (not the partial fallback)
        for r in p["results"]:
            if r["query"] not in chosen or chosen[r["query"]].get("partial"):
                chosen[r["query"]] = r
    results = []
    for q, r in chosen.items():
        items = [dict(zip(("title", "artistCredit"), t.rsplit(" — ", 1))) for t in r["top"]]
        expected = cases_by_query[q]["expected"]
        results.append({"query": q, "category": r["category"],
                        "rank": best_rank_by_title(items, expected),
                        "positions": expected_positions_by_title(items, expected), "top": r["top"]})
    return results


def run_rerank(args):
    """Tune each formula on the tuning set, then score every set once with the frozen parameters."""
    covers = load_covers()
    sets = {}
    for spec in args.set:
        name, cand, pop = spec.split(":")
        counts = json.loads(Path(pop).read_text())["counts"]
        sets[name] = (json.loads(Path(cand).read_text())["pools"], {k: tuple(v) for k, v in counts.items()})
    tune_pools, tune_counts = sets[args.tune]
    report = {"tune": args.tune, "selection": "hit@3, then hit@1, then artist nDCG@5, then MRR; smallest params",
              "formulas": {}, "references": {}}
    for name, (desc, grid, factory) in formulas(tune_counts).items():
        scored = [(selection_key(summarize(rerank(tune_pools, factory(p), covers))), -i, p)
                  for i, p in enumerate(grid)]
        best = max(scored, key=lambda t: t[:2])[2]
        entry = {"description": desc, "params": best,
                 "grid": [{"params": p, "key": k} for k, _, p in scored], "sets": {}}
        for set_name, (pools, counts) in sets.items():
            res = rerank(pools, formulas(counts)[name][2](best), covers)
            entry["sets"][set_name] = {"summary": summarize(res), "results": res}
        report["formulas"][name] = entry
        print(f"\n{name} {json.dumps(best)}  {desc}")
        for s_name, e in entry["sets"].items():
            print(f"   {s_name:8} {describe_stats(e['summary']['all'])}")
    for spec in args.reference or []:
        name, set_name, path = spec.split(":")
        cases = {p["query"]: p for p in sets[set_name][0]}
        res = reference_results(path, cases)
        report["references"].setdefault(name, {})[set_name] = {"summary": summarize(res), "results": res}
        print(f"\nreference {name} {set_name:8} {describe_stats(summarize(res)['all'])}")
    save_covers()
    # Per-query changes against A, so regressions are visible.
    print()
    for name, entry in report["formulas"].items():
        if name == "A":
            continue
        for set_name, e in entry["sets"].items():
            base = report["formulas"]["A"]["sets"][set_name]["results"]
            for ra, rb in zip(base, e["results"]):
                if ra["rank"] != rb["rank"]:
                    flag = "better" if (rb["rank"] or 99) < (ra["rank"] or 99) else "WORSE"
                    print(f"  {name} {set_name:8} {ra['query'][:28]:28} {ra['rank'] or '-'} → {rb['rank'] or '-'}  {flag}")
    write(args.out, report)


def run_artist_pages(args):
    """Adds each artist query's complete album list (paged past MusicBrainz's 100), for formula G: what
    ListenBrainz's token-only top-release-groups-for-artist would rank, which isn't capped at our 100."""
    path = Path(args.candidates)
    report = json.loads(path.read_text())
    for pool in report["pools"]:
        if not pool["artist_id"]:
            continue
        lucene = f"arid:{pool['artist_id']} AND primarytype:album AND NOT {EXCLUDED}"
        albums, offset, total = [], 0, None
        while total is None or offset < total:
            body = mb_get("release-group", {"query": lucene, "limit": POOL_LIMIT, "offset": offset})
            total = body.get("count", 0)
            groups = body.get("release-groups", [])
            if not groups:
                break
            albums += [_album(g) for g in groups if g.get("primary-type") in ("Album", "EP") and not _is_excluded(g)]
            offset += len(groups)
        pool["artist_albums_all"] = list({a["id"]: a for a in albums}.values())
        print(f"{pool['query'][:28]:28} {total} albums on MusicBrainz, {len(pool['artist_albums'])} in the search's first page")
    path.write_text(json.dumps(report, ensure_ascii=False, indent=1))


def run_compare(args):
    a, b = (json.loads(Path(f).read_text()) for f in (args.before, args.after))
    for pa, pb in zip(a["passes"], b["passes"]):
        print(f"\n=== {pa['label']} → {pb['label']}")
        for ra, rb in zip(pa["results"], pb["results"]):
            fa, fb = ra["rank"] or "-", rb["rank"] or "-"
            flag = "" if fa == fb else ("  better" if (rb["rank"] or 99) < (ra["rank"] or 99) else "  WORSE")
            print(f"  {ra['query'][:28]:28} {fa!s:>3} → {fb!s:<3}{flag}")
        sa, sb = pa["summary"]["all"], pb["summary"]["all"]
        for k in ("hit@1", f"hit@{TOP}", "hit@10", "mrr", f"ndcg@{ORDER_DEPTH}"):
            if k in sa and k in sb:
                print(f"  {k:7} {sa[k]} → {sb[k]}")


def write(path, data):
    if path:
        Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=1))
        print(f"\nreport written to {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="mode", required=True)
    e = sub.add_parser("endpoint")
    e.add_argument("--base", default="http://localhost:8082")
    e.add_argument("--passes", type=int, default=2)
    e.add_argument("--path", default="/albums/search")
    e.add_argument("--match", choices=["mbid", "title"], default="mbid")
    e.add_argument("--cases", default="queries.json")
    e.add_argument("--out")
    m = sub.add_parser("mb")
    m.add_argument("--out")
    k = sub.add_parser("candidates")
    k.add_argument("--cases", default="queries.json")
    k.add_argument("--parity", help="endpoint report (eval.py endpoint --out) to compare the mirror's top 10 with")
    k.add_argument("--out")
    p = sub.add_parser("popularity")
    p.add_argument("candidates", help="report from eval.py candidates")
    p.add_argument("--out")
    r = sub.add_parser("rerank")
    r.add_argument("--set", action="append", required=True, metavar="NAME:CANDIDATES:POPULARITY")
    r.add_argument("--tune", default="main", help="the set parameters are chosen on")
    r.add_argument("--reference", action="append", metavar="NAME:SET:REPORT",
                   help="an endpoint report to show alongside, rescored by title (e.g. Deezer)")
    r.add_argument("--out")
    a = sub.add_parser("artist-pages")
    a.add_argument("candidates", help="report from eval.py candidates; updated in place")
    c = sub.add_parser("compare")
    c.add_argument("before")
    c.add_argument("after")
    args = parser.parse_args()
    {"endpoint": run_endpoint, "mb": run_mb, "candidates": run_candidates, "popularity": run_popularity, "artist-pages": run_artist_pages, "rerank": run_rerank, "compare": run_compare}[args.mode](args)


if __name__ == "__main__":
    main()
