import type { AlbumDetails } from '../../api/types.ts'
import { Histogram } from '../ui/Histogram.tsx'
import { RatingStars } from '../ui/RatingStars.tsx'
import { SectionHeading } from '../ui/SectionHeading.tsx'
import { ToastRegion, useToast } from '../ui/Toast.tsx'
import { AlbumReviews } from './AlbumReviews.tsx'
import { MyRating } from './MyRating.tsx'
import { Sleeve } from './Sleeve.tsx'
import { useOneShot } from './useAlbumPage.ts'

/**
 * Album page, filled from GET /api/albums/{id}. Two columns on desktop: the sleeve hangs on the left
 * and stays put while the right column (title, my rating, the ratings, reviews) scrolls past it. On
 * mobile everything stacks in the same order (DESIGN.md section 8).
 * No dominant color stored yet, so the header has no wash and the record label uses the accent.
 * Rating and reviews are live. The average, count and histogram are built by catalog-service from
 * rating events; after I rate, they update at once and then settle on the server's numbers
 * (see showRatingChange in api/queries.ts). "Friends who listened" (FriendRatings) comes back once
 * friends' ratings of an album are served; until then the section would only ever say "none".
 */
export function AlbumView({ album }: { album: AlbumDetails }) {
  const toast = useToast()
  const spin = useOneShot()
  // Only rating moves the record here; sending and listen later aren't built yet.
  const idle = useOneShot()
  const date = releaseDate(album.firstReleaseDate)
  const facts = [album.primaryType, date].filter(Boolean).join(', ')

  return (
    <>
      <main className="mx-auto flex max-w-content flex-col gap-8 px-4 pt-6 pb-16 lg:flex-row lg:items-start lg:gap-16 lg:px-6 lg:pt-12">
        <Sleeve
          pinned
          title={album.title}
          artist={album.artistCredit}
          src={album.coverUrl}
          motion={{ spin, send: idle, nudge: idle }}
        />
        <div className="flex min-w-0 flex-1 flex-col">
          <header>
            <h1 className="font-narrow font-display text-h1 font-semibold text-balance text-text lg:text-display">
              {album.title}
            </h1>
            <p className="mt-3 font-display text-h4 font-medium text-text lg:mt-4 lg:text-h3">{album.artistCredit}</p>
            {facts && <p className="mt-1 text-body text-muted">{facts}</p>}
          </header>

          <section aria-label="Your rating" className="mt-8 border-t border-border pt-6 lg:mt-12">
            <MyRating albumId={album.id} title={album.title} toast={toast} onRated={spin.play} size="lg" />
          </section>

          <section className="mt-12 lg:mt-16">
            <SectionHeading>Ratings</SectionHeading>
            <RatingSummary album={album} />
          </section>

          <section className="mt-12 lg:mt-16">
            <SectionHeading>Reviews</SectionHeading>
            <AlbumReviews albumId={album.id} />
          </section>
        </div>
      </main>
      <ToastRegion toast={toast} />
    </>
  )
}

function RatingSummary({ album }: { album: AlbumDetails }) {
  if (album.ratingCount === 0 || album.avgRating === null) {
    return <p className="text-muted">No ratings yet. Be the first to rate it.</p>
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

const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

/** MusicBrainz dates can be partial: "1997" → "1997", "1997-05" → "May 1997", "1997-05-21" → "21 May 1997". */
export function releaseDate(date: string | null) {
  if (!date) return null
  const [year, month, day] = date.split('-')
  const monthName = month ? months[Number(month) - 1] : undefined
  if (!monthName) return year
  return day ? `${Number(day)} ${monthName} ${year}` : `${monthName} ${year}`
}
