# ADR-013: One release per album for length, label and tracklist

Date: 2026-10-14 · Status: accepted

## Context

An album in Crate is a MusicBrainz **release group**. Length, label and tracklist belong to a **release**,
one concrete edition: a pressing, a CD, a reissue, a deluxe version. A group can have dozens of them
(OK Computer: 39 official, Kind of Blue: 126), with different labels, countries and track counts.
The album page shows one facts line ("Album, 21 May 1997, 53 min, Parlophone") and one tracklist, so
it has to pick one release.

What the API gives us (checked 2026-10-14):
- A release-group lookup with `inc=releases` stops at **25 releases**.
- `GET /release?release-group={id}&status=official&inc=labels+media&limit=100` (a browse) returns up
  to 100 per page with their date, country, labels and track counts, **in no useful order**.
- `GET /release/{id}?inc=recordings+labels+release-groups+genres` returns one release's tracks
  (with lengths), labels **and the release group's genres** in one request.

So two requests per album: browse, then the chosen release. The rule decides which release.

## Decision

Browse the group's official releases (all statuses if there are none: some albums only exist as
bootlegs), then, in `ReleaseChoice`:

1. Only releases with tracks.
2. **The most common track count**: the standard edition. On a tie, the smaller count (bonus tracks only add).
3. **The most common label** among those. Each label counts once per release. On a tie, the label of
   the earliest release. Skipped when no release names a label.
4. **The earliest date.** A partial date ("1997") sorts after the full dates it contains; unknown last.
5. The MBID, so the same releases always give the same choice.

Genres come from the release group (most votes first, top 5), not the release: the group gathers the
votes. The album's date on the page stays the group's first release date, whichever release is chosen.

The tracklist is fetched lazily by its own endpoint (`GET /albums/{id}/tracks`), stored in
`album_releases` and `album_tracks`, and served from there afterwards. A group with no release with
tracks is stored as empty and asked again after 30 days.

## Results

Ten well-known albums, the first 100 official releases each, live data. "Matches" compares the label
with the album's well-known original label.

| Album | Official | Std. tracks | Earliest standard edition | Matches | + XW/XE/GB/US tie-break | **Label majority (chosen)** | Matches |
|---|---|---|---|---|---|---|---|
| OK Computer | 39 | 12 | 1997-05-21, JP, EMI | no (Parlophone) | same | **1997-06-16, XE, Parlophone** | yes |
| Abbey Road | 65 | 17 | 1969-09-26, GB, Apple Records | yes | same | same | yes |
| Nevermind | 94 | 12 | 1991-09-24, US, DGC | yes | XE, DGC + Sub Pop | 1991-09-24, US, DGC | yes |
| Rumours | 79 | 11 | 1977-02-04, US, Warner Bros. | yes | same | same | yes |
| Kind of Blue | 126 | 5 | 1959-08-17, US, Columbia | yes | same | same | yes |
| Blue | 28 | 10 | 1971-06-22, US, Reprise | yes | same | same | yes |
| Thriller | 80 | 9 | 1982-11-30, GB, Epic (vinyl) | yes | XW, Epic, a digital release dated 1982 | 1982-11-30, GB, Epic | yes |
| Discovery | 23 | 14 | 2001-02-26, AU, Parlophone | no (Virgin) | same (the tie is AU vs FR) | **2001-02-26, FR, Virgin** | yes |
| Currents | 16 | 13 | 2015-07-15, XW, Modular | yes | same | same | yes |
| Phoenix – Mugur de fluier | 4 | 14 | 1973, RO, no label | no (Electrecord) | same | **1987, RO, Electrecord** | yes |

- The earliest standard edition alone: **7/10**. OK Computer's earliest is a Japanese EMI CD, three
  weeks before the European Parlophone one.
- Preferring XW/XE/GB/US releases on the same date: still **7/10**. It doesn't touch the misses (OK
  Computer has no tie on its date; Discovery's tie is AU vs FR) and moves Thriller to a "1982" digital
  release, which is a data quirk.
- Label majority: **10/10**, and no extra request (labels are in the browse).

## Alternatives considered

- **Earliest official release.** Simplest, but often an import with bonus tracks (Japanese editions).
- **Country preference on ties (XW/XE/GB/US).** Measured above: no gain, one worse choice.
- **The artist's home country.** One more request per album (the artist's area), and fuzzy: UK artists
  release in XE, French artists in XE or FR, US labels license abroad.
- **Release-group data only.** Has genres and the date, but no tracks, length or label.

## Consequences

Good:
- The facts line names the label people know, and the tracklist is the standard edition's.
- The album page never waits for this: the tracklist loads on its own, and only the first person to
  open an album pays the two MusicBrainz requests (about 1–2 s on the shared 1 request/s limit).
- Failures stay local: MusicBrainz down means a 503 for the tracklist only, nothing stored, retried
  on the next request.

Bad:
- **Only the first 100 releases are considered** (one browse page, a deliberate shortcut). For groups
  with more, page order is arbitrary, so the choice can differ from one built on all of them. Of the ten
  above only Kind of Blue has more (126); with all of them the rule picks the same release.
- The tracklist may come from a reissue (Mugur de fluier: 1987) when the original has no label on
  MusicBrainz. The page still shows the group's first release date.
- Data is stored once and not refreshed yet (like album details; Phase 2: refresh after 30 days).
- Length and label only appear in the facts line once someone opened the tracklist; until then the line
  is "Album, 21 May 1997" and grows when the tracklist arrives.
