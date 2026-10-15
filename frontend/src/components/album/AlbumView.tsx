import type { AlbumDetails as Album } from '../../api/types.ts'
import { useAlbumTracks } from '../../api/queries.ts'
import { useElementHeight, useMediaQuery, useWindowHeight } from '../../hooks.ts'
import { Histogram } from '../ui/Histogram.tsx'
import { RatingStars } from '../ui/RatingStars.tsx'
import { SectionHeading } from '../ui/SectionHeading.tsx'
import { EmptyState } from '../ui/States.tsx'
import { TagList, TagListSkeleton } from '../ui/Tag.tsx'
import { ToastRegion, useToast } from '../ui/Toast.tsx'
import { AlbumDescription } from './AlbumDescription.tsx'
import { AlbumFooter } from './AlbumFooter.tsx'
import { AlbumReviews } from './AlbumReviews.tsx'
import { ListenLinks } from './ListenLinks.tsx'
import { MyRating } from './MyRating.tsx'
import { Sleeve } from './Sleeve.tsx'
import { sentence } from './text.ts'
import { AlbumTracklist } from './Tracklist.tsx'
import { useOneShot } from './useAlbumPage.ts'

/** Where the pinned column sits (the .album-aside rule): below the 64 px header, plus 32. */
const pinTop = 96
/** Room kept under a pinned column, so its last line never touches the window's edge. */
const pinBottom = 32

/**
 * Album page, filled from GET /api/albums/{id} (DESIGN.md section 8), laid out like Apple Music's album
 * view with the record hung on the wall: on desktop the left column is the object (sleeve, where to
 * listen, genres) and the right column is everything you read (title, artist, description, Your rating,
 * Ratings, Tracks with the facts under them, Reviews). On mobile it is one column, and the layout (grid
 * areas in index.css, .album-page) moves the genres down under the description; they are rendered once,
 * in the left column, so the pinned column stays whole on desktop.
 *
 * The left column is pinned only while all of it fits the window; otherwise it scrolls with the page.
 * No dominant color stored yet, so the header has no wash and the record label uses the accent.
 * Rating and reviews are live; after I rate, the stats update at once and then settle on the server's
 * numbers (see showRatingChange in api/queries.ts).
 */
export function AlbumView({ album }: { album: Album }) {
  const toast = useToast()
  const spin = useOneShot()
  // Only rating moves the record here; sending and listen later aren't built yet.
  const idle = useOneShot()
  const desktop = useMediaQuery('(min-width: 64rem)')
  const [asideRef, asideHeight] = useElementHeight<HTMLElement>()
  const windowHeight = useWindowHeight()
  const pinned = desktop && asideHeight > 0 && asideHeight <= windowHeight - pinTop - pinBottom
  const meta = [album.primaryType, album.year].filter(Boolean).join(' · ')

  return (
    <>
      <main className="album-page mx-auto max-w-content px-4 pt-6 pb-16 lg:px-6 lg:pt-12">
        <aside ref={asideRef} aria-label="About the album" className="album-aside shrink-0" data-pinned={pinned || undefined}>
          <div className="album-sleeve">
            <Sleeve title={album.title} artist={album.artistCredit} src={album.coverUrl} motion={{ spin, send: idle, nudge: idle }} />
          </div>
          <div className="album-listen mt-6">
            <ListenLinks albumId={album.id} title={album.title} artist={album.artistCredit} />
          </div>
          <div className="album-genres mt-6 empty:hidden">
            <Genres album={album} />
          </div>
        </aside>
        <div className="album-main">
          <header className="album-header mt-8 lg:mt-0">
            <h1 className="font-narrow font-display text-h1 font-semibold text-balance text-text lg:text-display">
              {album.title}
            </h1>
            {/* The artist is the album's name tag: accent as identity (DESIGN.md section 4.2). */}
            <p className="mt-3 font-display text-h4 font-medium text-accent-text lg:mt-4 lg:text-h3">
              {album.artistCredit}
            </p>
            {/* The only middle dot in the app (DESIGN.md section 12). */}
            {meta && <p className="mt-1 text-body text-muted">{meta}</p>}
            <div className="mt-6 empty:hidden">
              <AlbumDescription albumId={album.id} />
            </div>
          </header>

          <div className="album-reading">
            <section aria-label="Your rating" className="mt-8 border-t border-border pt-6 lg:mt-12">
              <MyRating albumId={album.id} title={album.title} toast={toast} onRated={spin.play} size="lg" />
            </section>

            <section className="mt-12 lg:mt-16">
              <SectionHeading>Ratings</SectionHeading>
              <RatingSummary album={album} />
            </section>

            <section className="mt-12 lg:mt-16">
              <SectionHeading>Tracks</SectionHeading>
              <AlbumTracklist albumId={album.id} trackCount={album.release?.trackCount} />
              <div className="mt-6">
                <AlbumFooter album={album} />
              </div>
            </section>

            <section className="mt-12 lg:mt-16">
              <SectionHeading>Reviews</SectionHeading>
              <AlbumReviews albumId={album.id} />
            </section>
          </div>
        </div>
      </main>
      <ToastRegion toast={toast} />
    </>
  )
}

/**
 * The genres as tags: from the album once anyone opened its tracklist, otherwise from the tracklist
 * request, holding their place with skeleton tags meanwhile. None known: nothing.
 */
function Genres({ album }: { album: Album }) {
  const tracks = useAlbumTracks(album.id)
  const genres = album.genres.length ? album.genres : (tracks.data?.genres ?? [])
  if (genres.length) return <TagList label="Genres" tags={genres.map(sentence)} />
  // Five pills, like most albums have, so they wrap like real tags do.
  if (!album.release && tracks.isPending) {
    return <TagListSkeleton widths={['Alternative rock', 'Art rock', 'Rock', 'Post-britpop', 'Experimental']} />
  }
  return null
}

function RatingSummary({ album }: { album: Album }) {
  if (album.ratingCount === 0 || album.avgRating === null) {
    return <EmptyState message="No ratings yet. Be the first to rate it." />
  }
  const count = album.ratingCount
  return (
    <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:gap-12">
      <div className="flex shrink-0 flex-col">
        <p className="tabular font-narrow font-display text-h1 font-semibold text-text">
          {album.avgRating.toFixed(1)}
          <span className="sr-only"> out of 5, average</span>
        </p>
        <span aria-hidden="true" className="mt-1">
          <RatingStars value={Math.round(album.avgRating * 2) / 2} className="text-body" />
        </span>
        <p className="tabular mt-1 text-meta text-muted">
          {count} rating{count === 1 ? '' : 's'}
        </p>
      </div>
      <div className="min-w-0 flex-1">
        <Histogram counts={album.ratingDistribution} size="lg" />
      </div>
    </div>
  )
}
