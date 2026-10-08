import type { AlbumDetails } from '../../api/types.ts'
import { Histogram } from '../ui/Histogram.tsx'
import { ToastRegion, useToast } from '../ui/Toast.tsx'
import { AlbumReviews } from './AlbumReviews.tsx'
import { FriendRatings } from './bits.tsx'
import { MyRating } from './MyRating.tsx'
import { SectionTitle } from './Reviews.tsx'
import { Sleeve } from './Sleeve.tsx'
import { useOneShot } from './useAlbumPage.ts'

/**
 * Album page (variant B with the record from C), filled from GET /api/albums/{id}.
 * No dominant color stored yet, so the header has no wash and the record label uses the accent.
 * Rating and reviews are live. The average, count and histogram are built by catalog-service from
 * rating events; after I rate, they update at once and then settle on the server's numbers
 * (see showRatingChange in api/queries.ts). Friends come from follows: until then that stays empty.
 */
export function AlbumView({ album }: { album: AlbumDetails }) {
  const toast = useToast()
  const spin = useOneShot()
  // Only rating moves the record here; sending and listen later aren't built yet.
  const idle = useOneShot()

  const facts = [
    ['Released', releaseDate(album.firstReleaseDate)],
    ['Type', album.primaryType],
  ].filter((fact): fact is [string, string] => Boolean(fact[1]))

  return (
    <>
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-content flex-col gap-6 px-4 py-8 lg:flex-row lg:gap-8 lg:px-6">
          <Sleeve
            title={album.title}
            artist={album.artistCredit}
            src={album.coverUrl}
            motion={{ spin, send: idle, nudge: idle }}
          />
          <div className="flex min-w-0 flex-1 flex-col gap-6">
            <div>
              <h1 className="font-narrow font-display text-h2 font-semibold text-text lg:text-h1">{album.title}</h1>
              <p className="mt-1 text-lead text-text">{album.artistCredit}</p>
            </div>
            {facts.length > 0 && (
              <dl className="grid max-w-prose grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
                {facts.map(([term, value]) => (
                  <div key={term} className="flex flex-col">
                    <dt className="text-meta text-muted">{term}</dt>
                    <dd className="text-body text-text">{value}</dd>
                  </div>
                ))}
              </dl>
            )}
            <div className="mt-auto">
              <MyRating albumId={album.id} title={album.title} notify={toast.show} onRated={spin.play} />
            </div>
          </div>
        </div>
      </header>

      <main className="relative mx-auto grid max-w-content gap-12 px-4 py-8 lg:grid-cols-3 lg:px-6">
        <section className="lg:col-span-2">
          <SectionTitle>Reviews</SectionTitle>
          <AlbumReviews albumId={album.id} />
        </section>
        <aside className="order-first flex flex-col gap-8 lg:order-none lg:border-l lg:border-border lg:pl-8">
          <section>
            <SectionTitle>Ratings</SectionTitle>
            {album.ratingCount > 0 && album.avgRating !== null ? (
              <>
                <p className="mb-3 flex items-baseline gap-2">
                  <span className="font-narrow font-display text-h3 font-semibold text-text">{album.avgRating.toFixed(1)}</span>
                  <span className="text-meta text-muted">average from {album.ratingCount}</span>
                </p>
                <Histogram counts={album.ratingDistribution} />
              </>
            ) : (
              <p className="text-muted">No ratings yet. Be the first to rate it.</p>
            )}
          </section>
          <section>
            <SectionTitle>Friends who listened</SectionTitle>
            <FriendRatings friends={[]} />
          </section>
        </aside>
      </main>
      <ToastRegion message={toast.message} />
    </>
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
