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

def load_cases():
    return json.loads((HERE / "queries.json").read_text())["cases"]


def best_rank(ids, expected):
    wanted = {e["mbid"] for e in expected}
    for i, item_id in enumerate(ids, 1):
        if item_id in wanted:
            return i
    return None


def summarize(results):
    def stats(rs):
        n = len(rs)
        ranks = [r["rank"] for r in rs]
        return {
            "n": n,
            f"hit@{TOP}": sum(1 for r in ranks if r and r <= TOP),
            "hit@10": sum(1 for r in ranks if r and r <= 10),
            "mrr": round(sum(1 / r for r in ranks if r) / n, 3) if n else 0,
        }
    by_cat = defaultdict(list)
    for r in results:
        by_cat[r["category"]].append(r)
    return {"all": stats(results), "by_category": {c: stats(rs) for c, rs in by_cat.items()}}


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
    a = s["all"]
    print(f"\nscore: hit@{TOP} {a[f'hit@{TOP}']}/{a['n']}   hit@10 {a['hit@10']}/{a['n']}   MRR {a['mrr']}")
    for c, st in s["by_category"].items():
        print(f"  {c:18} hit@{TOP} {st[f'hit@{TOP}']}/{st['n']}  hit@10 {st['hit@10']}/{st['n']}  MRR {st['mrr']}")
    return s


# ---------------------------------------------------------------- modes

def run_endpoint(args):
    cases = load_cases()
    passes = []
    for p in range(1, args.passes + 1):
        results = []
        for c in cases:
            url = f"{args.base}/albums/search?" + urlencode({"q": c["query"], "limit": 20})
            start = time.monotonic()
            body, status = http_get_json(url)
            ms = round((time.monotonic() - start) * 1000)
            if body is None:
                sys.exit(f"{url} failed: {status}")
            items = body["items"]
            rank = best_rank([i["id"] for i in items], c["expected"])
            # Search hides albums without a cover: say so when that's why an expected one is missing.
            returned = {i["id"] for i in items}
            no_cover = [] if rank and rank <= TOP else [
                f"{e['title']} — {e['artist']}" for e in c["expected"]
                if e["mbid"] not in returned and has_cover(e["mbid"]) is False]
            notes = (["partial"] if body.get("partial") else []) + [f"no cover: {t}" for t in no_cover]
            results.append({
                "query": c["query"], "category": c["category"], "rank": rank, "ms": ms,
                "partial": body.get("partial", False),
                "top": [f"{i['title']} — {i['artistCredit']}" for i in items[:10]],
                "no_cover": no_cover,
                "note": "; ".join(notes),
            })
        label = "cold" if p == 1 else "warm" if p == 2 else f"pass {p}"
        summary = print_results(f"endpoint {args.base}, pass {p} ({label})", results)
        ms = sorted(r["ms"] for r in results)
        print(f"  latency ms: median {ms[len(ms) // 2]}, max {ms[-1]}")
        dropped = [(r["query"], t) for r in results for t in r["no_cover"]]
        print("  expected albums missing because they have no cover: "
              + (", ".join(f"{t} ('{q}')" for q, t in dropped) if dropped else "none"))
        passes.append({"label": label, "results": results, "summary": summary})
    write(args.out, {"mode": "endpoint", "base": args.base, "passes": passes})


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


def run_compare(args):
    a, b = (json.loads(Path(f).read_text()) for f in (args.before, args.after))
    for pa, pb in zip(a["passes"], b["passes"]):
        print(f"\n=== {pa['label']} → {pb['label']}")
        for ra, rb in zip(pa["results"], pb["results"]):
            fa, fb = ra["rank"] or "-", rb["rank"] or "-"
            flag = "" if fa == fb else ("  better" if (rb["rank"] or 99) < (ra["rank"] or 99) else "  WORSE")
            print(f"  {ra['query'][:28]:28} {fa!s:>3} → {fb!s:<3}{flag}")
        sa, sb = pa["summary"]["all"], pb["summary"]["all"]
        for k in (f"hit@{TOP}", "hit@10", "mrr"):
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
    e.add_argument("--out")
    m = sub.add_parser("mb")
    m.add_argument("--out")
    c = sub.add_parser("compare")
    c.add_argument("before")
    c.add_argument("after")
    args = parser.parse_args()
    {"endpoint": run_endpoint, "mb": run_mb, "compare": run_compare}[args.mode](args)


if __name__ == "__main__":
    main()
