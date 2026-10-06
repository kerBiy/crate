import type { AlbumDetails } from '../../api/types.ts'
import { EmptyState } from '../ui/States.tsx'
import { RateControl } from './actions.tsx'
import { FriendRatings } from './bits.tsx'
import { Reviews, SectionTitle } from './Reviews.tsx'
import { Sleeve } from './Sleeve.tsx'

/**
 * Album page (variant B with the record from C), filled from GET /api/albums/{id}.
 * No dominant color stored yet, so the header has no wash and the record label uses the accent.
 * Ratings, reviews and friends have no backend yet: their empty states show, rating is disabled.
 */
export function AlbumView({ album }: { album: AlbumDetails }) {
  const facts = [
    ['Released', releaseDate(album.firstReleaseDate)],
    ['Type', album.primaryType],
  ].filter((fact): fact is [string, string] => Boolean(fact[1]))

  return (
    <>
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-content flex-col gap-6 px-4 py-8 lg:flex-row lg:gap-8 lg:px-6">
          <Sleeve title={album.title} artist={album.artistCredit} src={album.coverUrl} />
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
              <RateControl title={album.title} value={0} />
            </div>
          </div>
        </div>
      </header>

      <main className="relative mx-auto grid max-w-content gap-12 px-4 py-8 lg:grid-cols-3 lg:px-6">
        <section className="lg:col-span-2">
          <SectionTitle>Reviews</SectionTitle>
          <Reviews reviews={[]} state="empty" onRetry={() => {}} emptyMessage="No reviews yet." />
        </section>
        <aside className="order-first flex flex-col gap-8 lg:order-none lg:border-l lg:border-border lg:pl-8">
          <section>
            <SectionTitle>Ratings</SectionTitle>
            {album.ratingCount > 0 && album.avgRating !== null ? (
              <p className="flex items-baseline gap-2">
                <span className="font-narrow font-display text-h3 font-semibold text-text">{album.avgRating.toFixed(1)}</span>
                <span className="text-meta text-muted">average from {album.ratingCount}</span>
              </p>
            ) : (
              <EmptyState message="No ratings yet." />
            )}
          </section>
          <section>
            <SectionTitle>Friends who listened</SectionTitle>
            <FriendRatings friends={[]} />
          </section>
        </aside>
      </main>
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
