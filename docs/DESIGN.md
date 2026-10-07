# Crate — Design brief

> Source of truth for every UI decision. Lives in the repo as `docs/DESIGN.md`.
> Hex values and fonts below are **starting points**: refine them, but keep the intent.

---

## 1. The idea in one sentence

**An old vinyl shop where you dig through crates, that also feels like a small gallery of record art.**
The structure and logic of Letterboxd, adapted to albums, with its own personality.

Not ultra-modern, not generic AI. Calm and minimal by default; it comes alive when you *do* something.

---

## 2. Principles

1. **The covers are the design.** Album art carries the color. The UI around it stays quiet and neutral.
2. **Spend boldness in one place.** The signature (the vinyl peeking out of the sleeve, section 5) is the memorable thing. Everything else is disciplined.
3. **Calm at rest, pop on action.** No motion that the user didn't trigger, except one moment on the album page (section 6).
4. **Few words.** People don't read UI text. Every label does one job. Playful only in small doses (empty states, confirmations).
5. **Friendly, not cute.** Slightly rounded shapes, warm tones, a light touch of humor. No mascots, no emoji.

---

## 3. Structure (Letterboxd logic, for albums)

Borrow the *logic*, not the look. Never copy Letterboxd's logo, its brand colors, or its layouts pixel for pixel.

| Letterboxd | Crate |
|---|---|
| Poster grids | Square cover grids, medium size |
| Film page: poster + details + rate/watchlist actions + histogram + reviews | Album page: cover + details + Rate / Listen later / Send to a friend + histogram + friends' ratings + reviews |
| Four favorite films on the profile | Four favorite albums on the profile |
| Activity from friends | Friends feed: what your friends rated and reviewed |
| Watchlist | Listen later |

---

## 4. Visual system

### 4.1 Theme

- **Dark is the default** ("the shop after hours"). Light ("the shop in daylight") is fully supported.
- Themes are implemented **only through tokens** (CSS variables on `:root` and `[data-theme="light"]`), mapped into the Tailwind theme.
- **Components never use `dark:` variants or raw hex values.** They use semantic tokens (`bg`, `surface`, `text`, `muted`, `accent`…). Switching theme must require zero component changes.
- The user's choice is remembered. With no choice yet, use dark.

### 4.2 Color

Warm neutrals plus one accent: a **soft, warm, low-saturation orange** (apricot, not neon, not terracotta).

| Token | Dark (default) | Light | Use |
|---|---|---|---|
| `bg` | `#181512` | `#F4F3F1` | Page background |
| `surface` | `#221E1A` | `#FFFFFF` | Panels, sheets, inputs |
| `surface-raised` | `#2C2722` | `#FFFFFF` + border | Menus, popovers |
| `border` | `#3A342E` | `#E2DED8` | Dividers, input borders |
| `text` | `#EDE7DF` | `#1C1915` | Main text |
| `muted` | `#A39A8F` | `#6B635A` | Secondary text, meta |
| `accent` | `#E5935A` | `#C9733A` | Stars, primary buttons, focus ring, icons |
| `accent-text` | `#E5935A` | `#A85A26` | Accent used *as text* (needs AA contrast) |
| `on-accent` | `#1A1410` | `#FFFFFF` | Text on accent backgrounds |
| `danger` | `#D45D4C` | `#B8432F` | Errors, destructive actions |
| `skeleton` | `#221E1A` | `#EBE8E4` | Loading placeholders, dim end of the pulse |
| `skeleton-highlight` | `#2C2722` | `#E2DED8` | Bright end of the pulse |

Rules:
- **One accent only.** No secondary brand color.
- Never put text directly on raw cover colors.
- Every text/background pair must pass **WCAG AA**. Verify the values above and adjust them if they don't.
- The light background must stay neutral, **not cream**.
- The skeleton pair is its own token in light mode: `surface` and `surface-raised` are both white there and would vanish on `bg`.

### 4.3 Typography: modern-technical, with character

- **Display (headings, album titles, wordmark):** *Bricolage Grotesque*. Modern, but with quirks that feel human.
- **Text (everything else):** *Instrument Sans*. Clean, technical, very readable.
- Self-hosted (Fontsource), with real fallback stacks.
- **Not Inter, Roboto, Geist, Space Grotesk, or system fonts only.**

Type scale (px): `13 · 15 · 17 · 20 · 24 · 32 · 44`
- Body: 15–16, line-height ~1.5. Line length < 75 characters.
- Album title on the album page: 32 (mobile) / 44 (desktop), display face, tight letter-spacing.
- **Sentence case everywhere.** No all-caps labels.

### 4.4 Shape and spacing

- Spacing scale on a 4 px base: `4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`, plus a `1px` hairline (gaps between stars and histogram bars).
- Radius **follows hierarchy**, not one value for everything:
    - covers `4px` (sleeves are nearly square),
    - buttons and inputs `10px`,
    - sheets and modals `16px`,
    - avatars and pills `full`.
- **Shadows only on floating layers** (menus, modals, toasts). Never on cards or covers in a grid.
- Max content width ~1200 px. Content is left-aligned.

### 4.5 Icons

One set only, **Phosphor**: regular weight at rest, fill weight for the active state (for example the active tab). No emoji anywhere in the UI.

---

## 5. Signature: the vinyl in the sleeve

This is *the* memorable element. Nothing else competes with it.

- A stylized **SVG record**: dark disc, a few subtle grooves, a center label tinted with the album's color.
- **Desktop (pointer: fine):** on hover or keyboard focus over a cover in a grid, the sleeve **slides ~30% to the left while the record emerges on the right** (≈480 ms, `vinyl` token, `standard` ease-out) and both slide back on leave. Everything is clipped to the cover's own square: the record never overlaps neighbouring covers or the title text.
- **Album page:** the record rests **~⅓ out** of the 280 px sleeve, clearly visible as an object.
- **Mobile (no hover):** no hover effect in grids. On the album page, the record slides out **once** when the page opens.
- Only on covers ≥ ~96 px. Never on small thumbnails (feed rows, lists).
- `prefers-reduced-motion`: no sliding. The record is not shown in grids, and on the album page it is static at its resting position.

---

## 6. Album page: color from the cover

- The header takes the **album's dominant color** as a very subtle wash: ~18% opacity in dark mode, ~12% in light mode, fading into `bg` over ~400 px.
- **This is the only gradient allowed in the entire app.**
- The dominant color is computed **once per album and stored** (`dominant_color` on the album in catalog-service). This is a small backend task for a later phase. Until it exists, the header falls back to plain `bg`.
- Text in the header always sits on `bg`/`surface`-level contrast, never on the raw color.

---

## 7. Motion: calm, then pop

Tokens:
- Durations: `fast 180ms`, `base 300ms`, `slow 500ms`. Signature: `vinyl 480ms` (grid slide), `reveal 900ms` (album page record slides out on open), `spin 1000ms` (record spin), `stagger 70ms` (between stars). `toast 2400ms` (how long a toast stays up). `pulse 1600ms` (one loading pulse, dim to bright).
- Easing: `standard cubic-bezier(.22,1,.36,1)`, a smooth ease-out with a long soft landing. Pop: `cubic-bezier(.34,1.2,.64,1)`, an ease-out with only a slight overshoot, for action feedback. Pulse: `cubic-bezier(.45,0,.55,1)`, symmetric, for the loading breathe.
- Slow and smooth over snappy: motion should feel like handling a record, not a UI flicking.

At rest: nothing moves. **No fade-in on every section, and no hover effects on everything** (only covers get the vinyl).

**Loading is the one exception** (section 10): skeletons pulse in place between `skeleton` and `skeleton-highlight`, all in step. A pulse changes color only: no gradient sweep, nothing slides. Each cover fades in over `base` once its own image has loaded; that's the only fade-in in the app.

**The pop moments (only these):**

| Action | Reaction |
|---|---|
| Rate an album | Stars fill one by one with a soft pop (1 → 1.08 → 1). On the album page, the record does one slow spin. |
| Add to listen later | The cover gives a small "drop into the crate" nudge (down 4 px and back). Toast: "Added to listen later". |
| Follow someone | Button morphs into "Following" with a check. |
| Send to a friend | The record slides out of the sleeve toward the right, then a toast: "Sent to Ana". |

All of these respect `prefers-reduced-motion` (instant state change, no movement).

---

## 8. Layout

### Navigation
- **Desktop:** top bar with the wordmark `crate` (lowercase, display face) on the left; Search, Feed, Profile; avatar on the right.
- **Mobile:** bottom tab bar with three tabs: Feed, Search, Profile.

### Cover grids (medium, Letterboxd-like)
- 3 columns on mobile (390 px), 4 on tablet, 6 on desktop.
- Gap 12–16 px. Title and artist under the cover in `muted` text, max 2 lines, truncated.

### Album page (desktop)

```
┌───────────────────────────────────────────────────────────┐
│  [ cover-color wash fading into the background ]          │
│  ┌──────────┐◐                                            │
│  │  cover   │ (record)   OK Computer                      │
│  │  300px   │            Radiohead, 1997                   │
│  └──────────┘                                             │
│                          ★★★★½  Rate   Listen later  Send  │
│                          4.3 avg from 128   ▂▃▅▇█ (hist.) │
├───────────────────────────────────────────────────────────┤
│  Friends who listened   (o)(o)(o)(o) +3                    │
│  Reviews                                                   │
│  (o) ana   ★★★★★   "The best one, no debate."              │
└───────────────────────────────────────────────────────────┘
```

On mobile everything stacks: cover (full width, max 280 px), title, actions as a full-width row, then the rest.

---

## 9. Words

- Interface language: **English**.
- Minimal. Plain verbs, sentence case, no filler.
- **Buttons say exactly what happens**: "Rate", "Listen later", "Send to a friend", "Follow". The same word everywhere: the button "Listen later" leads to the "Listen later" list.
- Playful only in empty states and confirmations, one short line:
    - Empty feed: "Nothing spinning yet. Follow a friend to fill this crate."
    - Empty listen-later: "Your crate is empty. Save albums to listen to later."
    - No search results: "No records in this crate. Try another name."
- **Errors are plain and useful, never cute:** "Couldn't load reviews. Try again."
- No "Welcome to Crate!", no taglines, no marketing copy.

---

## 10. States (required for every data view)

- **Loading:** skeletons shaped like the real content (square cover blocks, text lines), pulsing slowly. No spinners in the middle of the page. With `prefers-reduced-motion` the pulse is static.
- **Covers load one by one** (Pinterest-style):
    - While the list itself loads, the whole grid is skeleton tiles (`AlbumTileSkeleton`): same square, same two caption lines.
    - Once results arrive, every tile keeps its exact square and pulses until *its* cover has loaded, then the image fades in (`base`, `standard`). No layout shift, no tile waits for another.
    - The vinyl hover/focus effect only works once the tile's cover is in (or has fallen back to initials). On the album page the record waits in the sleeve and slides out once the cover is in.
    - `front-250` in grids, `front-500` on the album page. Images are `loading="lazy"` and `decoding="async"`, except the first visible row (the first 6 tiles) and the album page cover, which load eagerly at high priority.
    - With `prefers-reduced-motion`: static placeholder, the image appears without a fade.
- **Empty:** one short line + one action.
- **Error:** what happened + a "Try again" button.
- **Missing cover:** a neutral sleeve with the album's initials in the display face. Search leaves out albums the Cover Art Archive confirmed have no cover; the album page still shows them with this sleeve.

---

## 11. Accessibility (non-negotiable)

- WCAG AA contrast in both themes.
- Visible focus ring in `accent` on every interactive element.
- Fully keyboard usable. The rating input uses arrow keys (half-star steps) and Enter to confirm.
- `prefers-reduced-motion` respected everywhere.
- Real `alt` text for covers: "Cover of OK Computer by Radiohead".
- Tap targets ≥ 44 px on mobile.

---

## 12. Banned

- Gradients (except the cover wash in section 6), glassmorphism, blur decorations
- Emoji as icons; stock illustrations; mascots
- Shadows on cards and covers; the same rounded card used for everything
- Generic hero sections, "Welcome to…" copy, taglines, lorem ipsum
- More than one accent color; neon; terracotta/clay oranges
- ALL-CAPS eyebrow labels, metadata joined with middle dots (`A · B · C`), monospace for data labels, arrows appended to button text (`Next →`)
- Fade-in animations on every section; hover effects on everything
- Inter / Roboto / Geist / Space Grotesk / system-only fonts
- Anything from Letterboxd's brand (logo, colors, dots)

---

## 13. Screens for Phase 1

| Screen | Purpose |
|---|---|
| Login / Register | Simple, centered form; the wordmark is the only decoration. Register asks for the invite code. |
| Search | One big input; results as a cover grid. |
| Album | Section 8 layout; the main screen of the app. |
| Profile | Four favorites on top, then a grid of rated albums; follow button; follower counts. |
| Friends feed | A list of activity rows: small cover, "ana rated OK Computer", stars, time. |

---

## 14. Definition of done for any UI task

1. Built only with tokens and the base components.
2. Screenshotted with Playwright at **390 px and 1440px**, in **both themes**.
3. Checked against this file, especially section 12 (Banned).
4. Loading, empty and error states exist and have been looked at.
5. Works with the keyboard alone.
---

## 15. Components

Base components live in `frontend/src/components/ui/`. Every state of each one is on `/dev/components` (development only).

- **Button**: any action. `primary` for the one main action in a view, `secondary` for the rest, `ghost` for low-key actions inside rows. `loading` while the action runs; `disabled` when it can't run yet. A link that acts as the main action uses `buttonStyles()`.
- **Input**: every text field, always with a visible label. `hint` for short help, `error` for what's wrong and how to fix it.
- **RatingStars**: showing a rating someone gave. Takes its size from the surrounding text.
- **RatingInput**: giving a rating. Half-star steps with the arrow keys, Enter confirms.
- **Cover**: album art at any size, in a square that never changes size. Pulses until its image loads, then fades in; falls back to a neutral sleeve with initials when the image is missing. `eager` for covers visible on arrival.
- **Vinyl**: the record, label tinted with the album color. Only with covers of 96 px or more.
- **AlbumTile**: one album in a cover grid; the record slides out on hover or focus, once the cover is in. **AlbumTileSkeleton**: the same tile while the list loads.
- **Histogram**: ratings spread on the album page. One Tab stop; hover, tap, focus or arrow keys show the count for each bar.
- **Avatar**: a person next to their name. `sm` in dense rows.
- **AvatarStack**: a group of people in one line, such as friends who listened, with "+N" for the rest.
- **Skeleton**: loading. Combine lines, blocks and circles into the shape of the real content; `text` sits inside a line of text so the line keeps its height. Pulses in place.
- **EmptyState**: a list with nothing in it. One line and at most one action.
- **ErrorState**: something failed to load. Plain message and "Try again".
- **Toast**: confirming an action that changed something. One at a time, via `useToast` and a single `ToastRegion` per page.
- **AppShell**: the frame of every page: top bar on desktop, bottom tabs on mobile.

Inline `style` is only for data: a bar's height, the album's color, a star's stagger index. ESLint rejects arbitrary Tailwind values, `dark:` variants and raw hex in components.
