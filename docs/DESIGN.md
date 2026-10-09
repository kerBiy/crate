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
6. **Clarity comes from type and space, not boxes.** Hierarchy is built from size, weight and leading together; groups are separated by generous space and inset hairlines, never by cards.

### Direction (v2)

The second visual pass takes its references from Apple's own apps, translated into the shop:

- **Apple Music's album view** for the album page: big art as the object in the room, a confident title, the artist one step below, and rows whose separators start at the text, not at the edge.
- **Large titles** (iOS) for top-level pages: the page names itself in display type, which answers "where am I?" without a breadcrumb.
- **The App Store's ratings summary**: the average as the headline number, the spread right next to it.

What we did **not** take: translucent materials, blur and shadows under content. They clash with the Banned list (section 12) and with "the covers are the design".

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
- Three choices in Settings: **Dark**, **Light**, **Match system** (follows the device's appearance, live). The choice is remembered. With no choice yet, use dark: the shop after hours is who we are, so following the device is opt-in.

### 4.2 Color

Warm neutrals plus one accent: a **soft, warm, low-saturation orange** (apricot, not neon, not terracotta).

| Token | Dark (default) | Light | Use |
|---|---|---|---|
| `bg` | `#181512` | `#F4F3F1` | Page background |
| `surface` | `#221E1A` | `#FFFFFF` | Panels, sheets, inputs |
| `surface-raised` | `#2C2722` | `#FFFFFF` + border | Menus, popovers |
| `border` | `#3A342E` | `#E2DED8` | Dividers and decorative edges only |
| `border-strong` | `#7A7066` | `#8F877D` | Edges of controls: inputs, secondary buttons, segmented controls |
| `text` | `#EDE7DF` | `#1C1915` | Main text |
| `muted` | `#A39A8F` | `#6B635A` | Secondary text, meta |
| `accent` | `#E5935A` | `#C9733A` | Stars, primary buttons, icons |
| `accent-pressed` | `#D1814A` | `#D68C57` | Primary button while pressed: one step toward the background |
| `accent-text` | `#E5935A` | `#A85A26` | Accent used *as text* (needs AA contrast) |
| `on-accent` | `#1A1410` | `#1C1915` | Text on accent backgrounds |
| `focus` | `#E5935A` | `#A85A26` | Focus ring (`accent` in dark, `accent-text` in light) |
| `pressed` | `#2C2722` | `#EBE8E4` | Secondary and ghost controls, tabs, while pressed |
| `danger` | `#D45D4C` | `#B8432F` | Errors, destructive actions |
| `skeleton` | `#221E1A` | `#EBE8E4` | Loading placeholders, dim end of the pulse |
| `skeleton-highlight` | `#2C2722` | `#E2DED8` | Bright end of the pulse |
| `cover-edge` | `text` at 12% | `text` at 12% | Hairline around loaded covers: keeps dark art from melting into a dark page, and white art from a light one. Decorative, like `border` |

Rules:
- **One accent only.** No secondary brand color.
- Never put text directly on raw cover colors.
- Every text/background pair must pass **WCAG AA** (4.5:1). Verify the values above and adjust them if they don't.
- Things you need to see to use the UI need **3:1** against what's around them: control edges (`border-strong`), the focus ring, chart bars. `border` is too faint for that (about 1.4:1) and is only for dividers, and for disabled controls, which should look out of reach.
- The light background must stay neutral, **not cream**.
- The skeleton pair is its own token in light mode: `surface` and `surface-raised` are both white there and would vanish on `bg`.

### 4.3 Typography: modern-technical, with character

- **Display (headings, album titles, wordmark):** *Bricolage Grotesque*. Modern, but with quirks that feel human.
- **Text (everything else):** *Instrument Sans*. Clean, technical, very readable.
- Self-hosted (Fontsource), with real fallback stacks.
- **Not Inter, Roboto, Geist, Space Grotesk, or system fonts only.**

Type scale (px): `13 · 15 · 17 · 20 · 24 · 32 · 44 · 64`

| Token | Size | Leading | Tracking | Use |
|---|---|---|---|---|
| `display` | 64 | 0.98 | `-0.035em` | Album title and page titles, desktop |
| `h1` | 44 | 1.02 | `-0.03em` | Album title and page titles, mobile; the average rating |
| `h2` | 32 | 1.08 | `-0.02em` | Stars of the rating input on the album page |
| `h3` | 24 | 1.3 | `-0.01em` | Section headings; the artist on the album page (desktop) |
| `h4` | 20 | 1.35 | `-0.005em` | The artist (mobile); the search field's text |
| `lead` | 17 | 1.5 | `0` | Reading text: reviews |
| `body` | 15 | 1.55 | `0` | Everything else; tile titles |
| `meta` | 13 | 1.4 | `+0.01em` | Secondary lines, dates, counts, field labels |

- **Hierarchy is a set: size, weight and leading together.** Big type gets tight leading and negative tracking; small type gets open leading and a little positive tracking. Emphasis comes from weight before size.
- **Tracking and leading follow size**, and live in the type tokens, never on a component.
- Page titles and album titles use the display face, narrow width (`font-narrow`), semibold, and `text-balance` so a two-line title breaks evenly.
- **Reading text is one step up.** Reviews are written to be read: `lead` (17), not `body`. Line length < 75 characters (`max-w-prose`).
- **Numbers that get compared use tabular figures** (`tabular`): averages, counts.
- **Sentence case everywhere.** No all-caps labels.

### 4.4 Shape and spacing

- Spacing scale on a 4 px base: `4 · 8 · 12 · 16 · 24 · 32 · 40 · 48 · 64 · 80`, plus a `1px` hairline (gaps between stars and histogram bars).
- **Rhythm:** pages start `24` below the header on mobile, `48` on desktop. Sections are `48` apart on mobile, `64` on desktop; a section heading sits `24` above its content. Inside a group, `4–12`.
- Named sizes, for things that have a fixed size of their own:

    | Token | Size | Use |
    |---|---|---|
    | `tap` | 44 px | Minimum tap target; buttons and inputs |
    | `avatar-sm` / `avatar` | 24 / 32 px | Avatars |
    | `thumb` | 56 px | Small covers in rows (feed); never the record |
    | `cover` | 280 px | Album page cover, mobile |
    | `cover-lg` | 360 px | Album page cover, desktop |
    | `panel` | 280 px | Album page sidebar |
    | `tabbar` | 64 px | Mobile tab bar height |

- **Only these sizes exist.** The Tailwind theme resets the default scale, so a class off it (`w-24`, `p-5`) produces no CSS at all and fails silently. Use a step of the scale, a named size, or a fraction (`w-1/3`) for proportional widths such as skeleton lines. A fraction is a share of its parent's width, so the parent needs one: inside a shrink-to-fit box (`items-start`, `inline-block`) it collapses to almost nothing. A new fixed size becomes a token here and in `index.css` first.
- Radius **follows hierarchy**, not one value for everything:
    - covers `4px` (sleeves are nearly square); `cover-lg` `8px` for the album page cover, since bigger art reads as a heavier object,
    - buttons and inputs `10px`,
    - sheets and modals `16px`,
    - avatars and pills `full`.
- **Shadows only on floating layers** (menus, modals, toasts). Never on cards or covers in a grid.
- **Covers have their own edge:** once the image is in, a `1px` `cover-edge` hairline sits just inside it. An edge, not a shadow: it doesn't lift the cover, it only says where the art ends.
- Max content width ~1200 px. Content is left-aligned.

### 4.5 Icons

One set only, **Phosphor**: regular weight at rest, fill weight for the active state (for example the active tab). No emoji anywhere in the UI.

---

## 5. Signature: the vinyl in the sleeve

This is *the* memorable element. Nothing else competes with it.

- A stylized **SVG record**: dark disc, a few subtle grooves, a center label tinted with the album's color.
- **Desktop (pointer: fine):** on hover or keyboard focus over a cover in a grid, the sleeve **slides ~30% to the left while the record emerges on the right** (≈480 ms, `vinyl` token, `standard` ease-out) and both slide back on leave. Everything is clipped to the cover's own square: the record never overlaps neighbouring covers or the title text.
- **Album page:** the record rests **~⅓ out** of the sleeve (`cover-lg` 360 px on desktop, up to 280 px on mobile), clearly visible as an object. On desktop the sleeve stays pinned in its column while the details scroll past it, like a record hung on the shop wall.
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
- Durations: `fast 180ms`, `base 300ms`, `slow 500ms`. Signature: `vinyl 480ms` (grid slide), `reveal 900ms` (album page record slides out on open), `spin 1000ms` (record spin), `stagger 70ms` (between stars). `toast 2400ms` (how long a toast stays up). `toast-action 6000ms` (a toast with an action such as Undo: long enough to reach it). `pulse 1600ms` (one loading pulse, dim to bright).
- Easing: `standard cubic-bezier(.22,1,.36,1)`, a smooth ease-out with a long soft landing. Pop: `cubic-bezier(.34,1.2,.64,1)`, an ease-out with only a slight overshoot, for action feedback. Pulse: `cubic-bezier(.45,0,.55,1)`, symmetric, for the loading breathe.
- **`pop` is only for the reward moments in the table below** (stars, Follow, the crate nudge). Overshoot says "you did something"; anywhere else it reads as wobble. Interface pieces (menus, toasts, pressed states, sheets) use `standard` and never overshoot.
- Slow and smooth over snappy: motion should feel like handling a record, not a UI flicking.

At rest: nothing moves. **No fade-in on every section, and no hover effects on everything** (only covers get the vinyl).

**Pressed is feedback, not decoration.** Everything you can press reacts on pointer-down, before the click lands: buttons, tabs and segmented controls take `pressed` (primary buttons `accent-pressed`), with no transition. Cover tiles shrink slightly while held (`scale .98`, `fast`, `standard`), since on touch they have no hover to show they're alive. This is not a hover effect and doesn't break "calm at rest".

**Previewing a value is feedback too.** The rating stars follow the pointer: hovering shows what that rating would look like, and pressing and dragging across the stars scrubs through half-steps under the finger. Nothing is saved until release (or Enter on the keyboard). Moving away puts the saved value back.

**Toasts enter and leave along the same path:** up `8px` and in over `base` / `standard`, then back down and out the same way. Reduced motion: fade only. A toast is a reaction to your action, so it isn't motion at rest.

**Loading is the one exception** (section 10): skeletons pulse in place between `skeleton` and `skeleton-highlight`, all in step. A pulse changes color only: no gradient sweep, nothing slides. Each cover fades in over `base` once its own image has loaded; that's the only fade-in in the app.

**The pop moments (only these):**

| Action | Reaction |
|---|---|
| Rate an album | Stars fill one by one with a soft pop (1 → 1.08 → 1). On the album page, the record does one slow spin. |
| Add to listen later | The cover gives a small "drop into the crate" nudge (down 4 px and back). Toast: "Added to listen later". |
| Follow someone | Button morphs into "Following" with a check. |
| Send to a friend | The record slides out of the sleeve toward the right, then a toast: "Sent to Ana". |

All of these respect `prefers-reduced-motion` (instant state change, no movement).

**Reduced motion means gentler, not nothing.** With `prefers-reduced-motion`, nothing moves, scales, spins or pulses. Fades that only change opacity stay, shortened to `fast`, because something popping in from nowhere is harsh too.

---

## 8. Layout

### Navigation
- **Desktop:** top bar with the wordmark `crate` (lowercase, display face) on the left; Search, Feed, Profile; avatar on the right.
- **Mobile:** bottom tab bar with three tabs: Feed, Search, Profile.
- **The tab says where you came from.** An album page keeps the tab you reached it from (Feed, Search or Profile); Search when opened directly. Someone else's profile shows no tab.
- **The header's edge appears only once content scrolls under it.** At the top of a page the header and the page are one surface; the `border` line fades in over the first `8px` of scroll. No blur, no shadow.
- **Settings** (theme, log out) is reached from your own profile, never from the main navigation: rare things live one level deeper.

### Page titles
- Top-level pages (Search, Feed, Profile, Settings) open with a **large title**: `h1` on mobile, `display` on desktop. It answers "where am I?"; the header doesn't repeat it.
- A control that changes the whole page sits on the title's row, at the end (Search's Albums / People).

### Cover grids (a gallery, Letterboxd logic)
- Fewer, bigger covers: **2 columns on mobile** (390 px), 3 small tablet, 4 tablet, **5 on desktop**.
- Gaps `16` across and `32` down on mobile, `24` and `40` on desktop: the space between rows carries the caption.
- Caption under the cover, one line each, truncated: the **title** (`body`, medium, `text`) and **"Artist, 1997"** (`meta`, `muted`). The year tells reissues and namesakes apart.

### Lists
- Rows (feed, people, reviews) are separated by **inset hairlines**: the `border` line starts where the text starts (after the avatar or thumbnail) and there is none after the last row. Full-width rules cut the page; inset ones only separate the items.

### Album page (desktop)

```
┌──────────────────────────────────────────────────────────────────┐
│  [ cover-color wash fading into the background ]                 │
│  ┌──────────────┐◐        OK Computer            (display, 64)    │
│  │              │         Radiohead               (h3)             │
│  │    cover     │ record  Album, 21 May 1997      (muted)          │
│  │    360px     │         ───────────────────────────────          │
│  │   (pinned)   │         Your rating                              │
│  └──────────────┘         ★ ★ ★ ★ ½   Edit review  Remove rating   │
│                                                                  │
│                           Ratings                                │
│                           4.3        ▁▂▃▅▇█▅▂  (tall histogram)    │
│                           ★★★★½      ½★              ★★★★★          │
│                           128 ratings                            │
│                                                                  │
│                           Reviews                                │
│                           (o) Ana                      8 Oct 2026 │
│                               ★★★★★                               │
│                               The best one, no debate.  (lead)    │
│                               ─────────────────── (inset rule)    │
└──────────────────────────────────────────────────────────────────┘
```

- Two columns: the sleeve on the left, pinned below the header; everything else in one column on the right, so the reading line never jumps.
- The facts are one quiet line, joined by a comma: "Album, 21 May 1997".
- "Your rating" is the main action: its own strip under a hairline, `h2` stars.
- Ratings: the average in `h1` with tabular figures, its stars and the count under it; the histogram next to it, taller (`80`), with the scale's ends (½ star, 5 stars) under the bars.

On mobile everything stacks in the same order: cover (record ⅓ out, up to 280 px), title (`h1`), artist (`h4`), facts, then the rest.

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
- **Undo beats "Are you sure?"** Removing something you made (a rating and its review) happens at once, with "Rating removed" and **Undo** in the toast. Confirmation dialogs only for what can't be undone.
- No "Welcome to Crate!", no taglines, no marketing copy.

---

## 10. States (required for every data view)

- **Loading:** skeletons shaped like the real content (square cover blocks, text lines), pulsing slowly. No spinners in the middle of the page. With `prefers-reduced-motion` the pulse is static.
- **Covers load one by one** (Pinterest-style):
    - While the list itself loads, the whole grid is skeleton tiles (`AlbumTileSkeleton`): same square, same two caption lines.
    - Once results arrive, every tile keeps its exact square and pulses until *its* cover has loaded, then the image fades in (`base`, `standard`). No layout shift, no tile waits for another.
    - The vinyl hover/focus effect only works once the tile's cover is in (or has fallen back to initials). On the album page the record waits in the sleeve and slides out once the cover is in.
    - `front-250` in grids, `front-500` on the album page. Images are `loading="lazy"` and `decoding="async"`, except the first row of the widest grid (the first 5 tiles) and the album page cover, which load eagerly at high priority.
    - With `prefers-reduced-motion`: static placeholder, the image cross-fades in over `fast`.
- **A new search keeps the old results up.** While the next query loads, the previous results stay in place, dimmed, and the list is marked busy. The skeleton grid is only for the very first search. Enter searches right away instead of waiting for the pause after typing.
- **Empty:** one short line + one action. The line is `lead` in `text` (it's the content of the view now, not a footnote), with an optional Phosphor icon in `muted` above it that names the place (a record for an empty crate, a magnifier for no results).
- **Error:** what happened + a "Try again" button.
- **Missing cover:** a neutral sleeve with the album's initials in the display face. Search leaves out albums the Cover Art Archive confirmed have no cover; the album page still shows them with this sleeve.

---

## 11. Accessibility (non-negotiable)

- WCAG AA contrast in both themes: 4.5:1 for text, 3:1 for control edges, the focus ring and chart bars.
- Visible focus ring in `focus` on every interactive element.
- Fully keyboard usable. The rating input uses arrow keys (half-star steps) and Enter to confirm; pointer scrubbing (section 7) is an extra, never the only way.
- A toast's action is reachable without a mouse: when the control you used disappears (Remove rating), focus moves to the toast's action, and back to the stars when the toast closes. The toast never closes while it's hovered or has keyboard focus (time limits are the user's to control); Escape closes it.
- Escape closes an editor (such as the review) without losing what was typed: reopening it brings the draft back. Only Cancel throws a draft away.
- `prefers-reduced-motion` respected everywhere (section 7).
- `prefers-contrast: more`: every edge uses `border-strong` (dividers too) and `muted` text moves closer to `text`.
- Respect the device's edges: the page draws under the iPhone's rounded corners and home indicator (`viewport-fit=cover`) and pads itself with the safe-area insets; full-height pages use dynamic viewport height (`dvh`) so the browser toolbar doesn't hide their bottom.
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
| Settings | Theme (Dark / Light / Match system) and Log out. Reached from your own profile. |

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
- **Input**: every text field, always with a visible label and a `border-strong` edge. `hint` for short help, `error` for what's wrong and how to fix it.
- **RatingStars**: showing a rating someone gave. Takes its size from the surrounding text.
- **RatingInput**: giving a rating. `size="lg"` on the album page, where it is the main action (`h2` stars, same 44 px cells). Hover previews, press-and-drag scrubs, release commits; half-star steps with the arrow keys, Enter confirms. On the album page it sits under a label that says what it is ("Rate this album", then "Your rating"), with its review and remove actions right below it.
- **Cover**: album art at any size, in a square that never changes size. Pulses until its image loads, then fades in with its `cover-edge` hairline; falls back to a neutral sleeve with initials when the image is missing. `eager` for covers visible on arrival. `size`: `sm` in rows, `md` in grids, `lg` on the album page (`cover-lg` corners, bigger initials).
- **Sleeve**: the album page cover with its record. `pinned` keeps it in place while its column scrolls (desktop).
- **Vinyl**: the record, label tinted with the album color. Only with covers of 96 px or more.
- **AlbumTile**: one album in a cover grid; the record slides out on hover or focus, once the cover is in. Caption: title, then "Artist, year". **AlbumTileSkeleton**: the same tile while the list loads.
- **Histogram**: ratings spread on the album page. One Tab stop; hover, tap, focus or arrow keys show the count for each bar. Bars in `muted`, the most common rating in `text`, the one being read in `accent`. `size="lg"`: taller bars with the scale's ends under them.
- **Avatar**: a person next to their name. `sm` in dense rows.
- **AvatarStack**: a group of people in one line, such as friends who listened, with "+N" for the rest.
- **Skeleton**: loading. Combine lines, blocks and circles into the shape of the real content; `text` sits inside a line of text so the line keeps its height. Pulses in place.
- **EmptyState**: a list with nothing in it. One line in `lead`, an optional icon above it, and at most one action.
- **PageHeader**: the large title of a top-level page, with an optional control at the end of its row.
- **SectionHeading**: a section inside a page ("Ratings", "Reviews"): `h3`, `24` above its content.
- **SearchField**: the one field of the Search page. A visible label, a magnifier inside, a clear button once there is text (it puts focus back in the field), Enter searches now.
- **Rows** (`FeedRow`, `PersonRow`, review rows): put them in a list with the `rows` class to get inset separators; each row marks its text column with `row-rule`.
- **ErrorState**: something failed to load. Plain message and "Try again".
- **SegmentedControl**: two or three mutually exclusive choices side by side, the chosen one raised: what Search looks for, the theme. Not for navigation (that's the tabs).
- **Toast**: confirming an action that changed something. One at a time, via `useToast` and a single `ToastRegion` per page. At most **one action** ("Undo"); a toast with an action stays up for `toast-action` and pauses while hovered or focused from the keyboard.
- **AppShell**: the frame of every page: top bar on desktop, bottom tabs on mobile.

Inline `style` is only for data: a bar's height, the album's color, a star's stagger index. ESLint rejects arbitrary Tailwind values, `dark:` variants and raw hex in components.
