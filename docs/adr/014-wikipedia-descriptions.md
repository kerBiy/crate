# ADR-014: The album's Wikipedia intro as its description

Date: 2026-10-14 · Status: accepted

## Context

DESIGN.md keeps a slot on the album page, under "Album · 1997", for a short introduction to the album.
English Wikipedia has one for most well-known albums, and its text may be reused under CC BY-SA. The
question is how to find the right article for a MusicBrainz release group, how to fetch it within
Wikimedia's rules, and how to credit it.

What the APIs give us (checked 2026-10-14):
- `GET /ws/2/release-group/{id}?inc=url-rels` lists the group's links to other sites. Albums are linked to
  **Wikidata** (`type: "wikidata"`, e.g. OK Computer → `Q202996`); a direct `type: "wikipedia"` link is
  rare now (MusicBrainz moved most of them to Wikidata). Links can be marked `ended`.
- `GET https://www.wikidata.org/w/api.php?action=wbgetentities&ids=Q202996&props=sitelinks&sitefilter=enwiki&format=json&formatversion=2`
  gives the English article's title, or an empty `sitelinks` when there is none (Phoenix, *Mugur de
  fluier*: `Q12735998`, a Wikidata item but no English article).
- `GET https://en.wikipedia.org/api/rest_v1/page/summary/{title}` gives the article's first paragraph as
  plain text (`extract`) and HTML (`extract_html`), its canonical address (`content_urls.desktop.page`) and
  a `type`: `standard` for an article, `disambiguation` for a page listing several things of that name.
  404 when there is no such page.

## Wikimedia's rules (summarized)

- **User-Agent** ([User-Agent policy](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy)):
  `<client>/<version> (<contact>) <library>/<version>`. Generic or missing agents "may be blocked without
  notice". We send `Crate/0.1 ( <MUSICBRAINZ_CONTACT> ) Java-http-client/25`.
- **Rate limits** ([Wikimedia APIs/Rate limits](https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits), new in 2026):
  10 requests/minute for a client identified only by IP, **200/minute with a proper User-Agent**, 2000
  for authenticated editors; for the Action API and the REST APIs alike. Over the limit: 429, usually
  with `Retry-After` (otherwise wait at least 5 s).
- **Etiquette** ([API:Etiquette](https://www.mediawiki.org/wiki/API:Etiquette)): requests in series, not
  in parallel; cache what can be cached instead of asking again.
- **The summary endpoint** ([Page Content Service](https://www.mediawiki.org/wiki/Page_Content_Service)) is
  marked stable. The RESTBase sunset ([API Portal/Deprecation](https://wikitech.wikimedia.org/wiki/API_Portal/Deprecation))
  removes other endpoints (`/page/related`, `/page/data-parsoid`) and the api.wikimedia.org domain; the
  per-wiki `/api/rest_v1` URLs are the ones that stay. A risk to watch, not a blocker.
- **License** ([Reusing Wikipedia content](https://en.wikipedia.org/wiki/Wikipedia:Reusing_Wikipedia_content)):
  the text is CC BY-SA 4.0 ([license](https://creativecommons.org/licenses/by-sa/4.0/)). Reusing it needs
  (1) credit: a link to the article is enough, (2) a license notice with a link to the license text,
  (3) a note if we changed the text. **Share-alike** applies to modified versions: whatever we change must
  be under CC BY-SA too. It covers that text, not Crate's code or the rest of the page.

## Decision

`GET /albums/{id}/description`, loaded by the album page on its own (like the tracklist, ADR-013), so the
page never waits for it:

1. The release group's url-rels (one MusicBrainz request, through the shared MusicBrainz rate limiter).
   A current direct English Wikipedia link wins; otherwise its current Wikidata item. Other languages and
   ended links are ignored.
2. With a Wikidata item: its `enwiki` sitelink (one Wikidata request).
3. The article's summary (one Wikipedia request). Only `type: standard` with text counts: a
   disambiguation page is about several things, not this album.
4. Stored in `album_descriptions`: the plain-text `extract`, the article's address and title, or "no
   English article" (text null). Served from there for **30 days**, the "none" answer too, then fetched
   again (articles get edited, and written).

Wikidata and Wikipedia calls share their own token bucket at **1 request/second** (a first view needs at
most two), separate from MusicBrainz's and ListenBrainz's: one service's burst never spends another's
budget. 3 s timeout, no retry. Any failure (timeout, 429, 5xx, a full queue, MusicBrainz down) stores
nothing and answers **503 `description-unavailable`**; the next view tries again. The page shows nothing
in that case: a description is a nice-to-have, never an error.

**Credit on the page:** "From Wikipedia" (linked to the article) and "CC BY-SA 4.0" (linked to the
license), right under the text. The text is shown unchanged; clamping it to three lines with More is
presentation, not a modification, so there is no "changed" note to make.

## Alternatives considered

- **`extract_html`** (keeps the italics of titles). Means rendering third-party HTML: a sanitizer on our
  side, or trusting Wikipedia's output not to carry anything active. Plain text has no such risk; the
  italics aren't worth it.
- **The Action API's TextExtracts** (`action=query&prop=extracts&exintro`). Same text, but Page Content
  Service no longer uses it for summaries, and it needs more parameters for the same result. The REST
  summary also gives the canonical URL and the page type in one call.
- **Searching Wikipedia by "title (album)".** No MusicBrainz request, but a guess: namesakes, other
  artists' albums, missing "(album)" suffixes. Wikidata is the curated link between the two databases.
- **Wikidata's short description** ("1997 studio album by Radiohead"). Always there, but says nothing
  the meta line doesn't already.
- **Fetching it as part of `GET /albums/{id}`.** That endpoint is also review-service's existence check
  and must never wait on optional data (ADR-012).

## Consequences

Good:
- The page never waits: the description arrives on its own, and only the first view of an album pays
  the up-to-three requests (about 1.5 s measured for a first view, 20 ms afterwards).
- Well inside Wikimedia's limits (1/s ≪ 200/min), identified, serial, cached: what their etiquette asks.
- A failure stays local to the description: nothing stored, retried on the next view.

Bad:
- English only. An album without an English article has no description, even if other Wikipedias
  have one (Mugur de fluier has a Romanian one).
- The text can be up to 30 days behind Wikipedia.
- The summary endpoint lives on RESTBase-era URLs. If it moves, the client's one path changes; until
  then a 404 would read as "no article" for 30 days, so watch the logs if descriptions disappear.
- Share-alike binds us if we ever edit the text (shorten it, translate it): the edited text must stay
  CC BY-SA and say it was changed.
