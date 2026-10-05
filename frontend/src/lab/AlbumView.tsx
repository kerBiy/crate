import type { CSSProperties } from 'react'
import { ListenLaterButton, RateControl, SendButton, Toast } from '../components/album/actions.tsx'
import { FriendRatings } from '../components/album/bits.tsx'
import { Histogram } from '../components/album/Histogram.tsx'
import { Reviews, SectionTitle, type DataState } from '../components/album/Reviews.tsx'
import { Sleeve } from '../components/album/Sleeve.tsx'
import { useAlbumPage } from '../components/album/useAlbumPage.ts'
import type { Album } from './albums.ts'

type AlbumViewProps = {
  album: Album
  state: DataState
  onRetry: () => void
}

/**
 * Album page: condensed title, facts up front, a band tinted with the cover color, the record
 * well out of the sleeve. Body is two columns on desktop; on mobile ratings and friends come first.
 */
export function AlbumView({ album, state, onRetry }: AlbumViewProps) {
  const page = useAlbumPage()
  const facts = [
    ['Released', album.released],
    ['Length', album.length],
    ['Tracks', String(album.tracks)],
    ['Label', album.label],
  ]

  return (
    <>
      <header className="wash-band border-b border-border" style={{ '--album': album.dominantColor } as CSSProperties}>
        <div className="mx-auto flex max-w-content flex-col gap-6 px-4 py-8 lg:flex-row lg:gap-8 lg:px-6">
          <Sleeve album={album} page={page} />
          <div className="flex min-w-0 flex-1 flex-col gap-6">
            <div>
              <h1 className="font-narrow font-display text-h2 font-semibold text-text">{album.title}</h1>
              <p className="mt-1 text-lead text-text">{album.artist}</p>
            </div>
            <dl className="grid max-w-prose grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
              {facts.map(([term, value]) => (
                <div key={term} className="flex flex-col">
                  <dt className="text-meta text-muted">{term}</dt>
                  <dd className="text-body text-text">{value}</dd>
                </div>
              ))}
            </dl>
            <div className="mt-auto flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:gap-3">
              <RateControl album={album} page={page} />
              <div className="grid grid-cols-2 gap-3 sm:flex">
                <ListenLaterButton page={page} />
                <SendButton page={page} />
              </div>
            </div>
          </div>
        </div>
      </header>

      <main className="relative mx-auto grid max-w-content gap-12 px-4 py-8 lg:grid-cols-3 lg:px-6">
        <section className="lg:col-span-2">
          <SectionTitle>Reviews</SectionTitle>
          <Reviews reviews={album.reviews} state={state} onRetry={onRetry} />
        </section>
        <aside className="order-first flex flex-col gap-8 lg:order-none lg:border-l lg:border-border lg:pl-8">
          <section>
            <SectionTitle>Ratings</SectionTitle>
            <p className="mb-3 flex items-baseline gap-2">
              <span className="font-narrow font-display text-h3 font-semibold text-text">{album.average.toFixed(1)}</span>
              <span className="text-meta text-muted">average from {album.ratingCount}</span>
            </p>
            <Histogram album={album} />
          </section>
          <section>
            <SectionTitle>Friends who listened</SectionTitle>
            <FriendRatings friends={album.friends} />
          </section>
        </aside>
      </main>
      <Toast message={page.toast} />
    </>
  )
}
