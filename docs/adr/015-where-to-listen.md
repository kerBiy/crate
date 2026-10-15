# ADR-015: Where to listen: MusicBrainz links, search as the fallback

Date: 2026-10-14 · Status: accepted

## Context

Crate is for logging albums, not playing them, so the album page should send people to wherever they
listen: Spotify, Apple Music, YouTube Music, and others. The questions: where exact links come from,
what to show when there are none, and how to show other brands' marks inside our design.

## Evaluation

**MusicBrainz url-rels**, measured on the 31 albums of the search-eval sets (`queries.json` 22 unique,
`queries-niche.json` 9), one page of releases each (2026-10-14):

- On release groups: **0 of 31.** Every streaming link sits on a release (usually a digital one).
- `GET /release?release-group=…&inc=labels+media+url-rels` is accepted. That is the browse the
  tracklist already makes (ADR-013), so the links cost **no extra request**.

| Platform | Well-known (22) | Niche (9) |
|---|---|---|
| Any link | 22 | 5 |
| Spotify | 22 | 5 |
| Apple Music | 22 | 4 |
| Deezer | 20 | 4 |
| Qobuz | 19 | 4 |
| Tidal | 17 | 5 |
| YouTube Music (music.youtube.com) | 6 | 0 |
| Bandcamp | 4 | 3 |
| SoundCloud, Amazon Music | 1, 2 | 0, 0 |

The niche albums without any link are the three Romanian ones and *Money Jungle*. MusicBrainz also often
has a `www.youtube.com/playlist?list=OLAK5uy_…` link: YouTube's auto-generated album playlist, the same
album on YouTube Music. Popular albums have links on many releases (1989: 17 releases with a Spotify
link, deluxe and radio editions among them), so one has to be picked.

**Odesli (song.link)**, a link aggregator (one link in, the album on every platform out):

- Without a key, the live API answers `401 {"code":"PUBLIC_API_ACCESS_DEPRECATED"}` (2026-10-14).
  Older docs and client libraries still describe keyless access at 10 requests/minute; that is gone.
- A key only by emailing developers@song.link. The terms (odesli.co/terms) redirect to a Google Drive
  file behind a login: we couldn't read them.
- It needs a link to start from, so it can't help the albums that have none.

**Search links** (`open.spotify.com/search/{q}`, `music.apple.com/search?term={q}`,
`music.youtube.com/search?q={q}`): always available, no API, no key; they land on results, not on
the album.

## Decision

1. **Exact links from MusicBrainz**, read from the releases' url-rels in the tracklist's browse and
   stored with it (`album_listen_links`, served in `GET /albums/{id}/tracks`). `ListenPlatform`
   recognizes album links on Spotify, Apple Music, YouTube Music (the `OLAK5uy_` playlists rewritten to
   music.youtube.com), Deezer, Tidal, Bandcamp and SoundCloud; ended links are dropped. Qobuz and Amazon
   Music would be one more enum value each, once we want them.
2. **One link per platform** (`ListenLinkChoice`): the chosen release's own (ADR-013); else one from a
   release with the same track count (the same edition, most likely its digital copy); else any release,
   because a deluxe edition's link beats no link. Earliest first, then MBID, so the choice is stable.
3. **On the page: Spotify, Apple Music and YouTube Music, always all three**, as icon-only links under
   the cover. An exact link opens the album ("Open on Spotify"); without one, the platform's search for
   "artist title" ("Search on Spotify"): the label never calls a search an album link. The other
   platforms are stored for later, not shown: three marks are recognizable at a glance, seven are a
   logo wall.
4. **No Odesli**: a secret, a third party whose terms we can't read, and nothing for the albums that
   most need help.

Search links are built by the frontend from the title and artist; they are not stored.

## The marks: brand guidelines and sources

DESIGN.md allows Phosphor icons and one accent color. Here we make one written exception (DESIGN.md
sections 4.5 and 12): the three platforms appear as their **own app icons, in their own colors,
unmodified**, all at the same size (32 px, in a 44 px tap target), only in Where to listen.

- **Spotify**: the Primary Logo (the icon without the wordmark) from Spotify's 2024 brand assets,
  `https://storage.googleapis.com/pr-newsroom-wp/1/2023/05/2024-Spotify-Brand-Assets.zip`
  (`Spotify_Primary_Logo_RGB_Green.png`), linked from newsroom.spotify.com/media-kit/logo-and-brand-assets.
  [Design guidelines](https://developer.spotify.com/documentation/design): the icon alone "only if you do
  not have enough room for the full logo"; never smaller than 21 px; green, black or white only; green on
  black or white backgrounds. Our 32 px icon in a row of three is the "not enough room" case. The waves
  are cut out, so they take the page color: near-black in dark mode, near-white in light mode.
- **Apple Music**: the 2020 Apple Music icon, color RGB, the "Small" SVG drawn for small sizes
  (`Apple_Music_Icon_RGB_sm_073120.svg`), from the Download link in Apple's
  [Apple Music Identity Guidelines](https://marketing.services.apple/apple-music-identity-guidelines)
  (`apple.co/am-icon-2020`, a zip on Apple's Box). The guidelines: "The Apple Music icon can be used
  alongside other social media icons that are similar in shape or size", and "to link to music content
  within apps, where the Apple Music badge does not fit"; color is preferred; "Do not create your own icon.
  Only the versions shown here are approved." So not Phosphor's Apple logo.
- **YouTube Music**: **no public brand kit.** brand.youtube covers YouTube only, and Google's
  Partner Marketing Hub (where the YouTube Music kit lives) needs a partner sign-in. We use the icon
  YouTube Music itself serves, `https://music.youtube.com/img/favicon_144.png`, unmodified. It is
  Google's own artwork and what people recognize, but it doesn't come with published usage terms.
  Replace it with the kit's file if we ever get access. YouTube's general rule, "the icon should never be
  altered", is followed.

The files live in `frontend/src/assets/listen/`, byte-for-byte as downloaded.

## Consequences

Good:
- No extra request and no new dependency: the links ride on a browse we already make, and are cached
  with the tracklist.
- Exact links for the albums people know (22/22 on Spotify and Apple Music); the three icons are always
  there, and say when they only search.
- A failure stays small: no tracklist means three search links, never an error.

Bad:
- **Storing links reset the tracklist cache.** `V10__album_listen_links.sql` empties `album_releases`
  (and its tracks): rows stored before had no links. Each album fetches them again on its next view
  (two MusicBrainz requests, like a first view); until then its label, length and genres wait for the
  tracklist, as on a first view.
- Links can go stale: they are refreshed only with the tracklist (never, until Phase 2's refresh).
- YouTube Music has few direct links (6/22); for most albums its icon searches.
- **Apple storefronts.** MusicBrainz stores a country (`music.apple.com/gb/album/…`). Checked
  2026-10-14: `/gb/` stays on the UK store (only a 301 to add the album's slug), while
  `geo.music.apple.com/album/{id}` sends each visitor to their own store (from Romania: `/ro/`).
  We keep MusicBrainz's link for now: an album ID from one store may not exist in another, and a
  geo link would then fail. Revisit if people land on the wrong store.
- The YouTube Music icon has no published terms behind it (see above).
- One more exception in DESIGN.md: brand colors and a gradient (Apple Music's) next to our single accent,
  kept to this one row.
