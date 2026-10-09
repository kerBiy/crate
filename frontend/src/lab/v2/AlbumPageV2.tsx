import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { useAlbum } from '../../api/queries.ts'
import type { AlbumDetails } from '../../api/types.ts'
import { AlbumReviews } from '../../components/album/AlbumReviews.tsx'
import { releaseDate } from '../../components/album/AlbumView.tsx'
import { MyRating } from '../../components/album/MyRating.tsx'
import { Sleeve } from '../../components/album/Sleeve.tsx'
import { useOneShot } from '../../components/album/useAlbumPage.ts'
import { buttonStyles } from '../../components/ui/Button.tsx'
import { Histogram } from '../../components/ui/Histogram.tsx'
import { RatingStars } from '../../components/ui/RatingStars.tsx'
import { SectionHeading } from '../../components/ui/SectionHeading.tsx'
import { Skeleton } from '../../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../../components/ui/States.tsx'
import { ToastRegion, useToast } from '../../components/ui/Toast.tsx'

/** v2 proposal of the album page, with real data. Lab only: /lab/v2/album/:id. */
export function AlbumPageV2() {
  const { id = '' } = useParams()
  const album = useAlbum(id)

  if (album.isPending) return <AlbumSkeletonV2 />

  if (album.isError) {
    const missing = album.error instanceof ApiError && (album.error.status === 404 || album.error.status === 400)
    return (
      <main className="mx-auto max-w-content px-4 py-12 lg:px-6">
        {missing ? (
          <EmptyState
            message="Couldn't find this album."
            action={
              <Link to="/lab/v2/search" className={buttonStyles('primary')}>
                Search albums
              </Link>
            }
          />
        ) : (
          <ErrorState message="Couldn't load this album." onRetry={() => album.refetch()} retrying={album.isFetching} />
        )}
      </main>
    )
  }

  return <AlbumViewV2 key={album.data.id} album={album.data} />
}

/**
 * Two columns on desktop: the sleeve hangs on the left and stays put while the right column
 * (title, my rating, the ratings, reviews) scrolls past it. On mobile everything stacks.
 */
function AlbumViewV2({ album }: { album: AlbumDetails }) {
  const toast = useToast()
  const spin = useOneShot()
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

function AlbumSkeletonV2() {
  return (
    <div
      aria-busy="true"
      aria-label="Loading album"
      className="mx-auto flex max-w-content flex-col gap-8 px-4 pt-6 pb-16 lg:flex-row lg:items-start lg:gap-16 lg:px-6 lg:pt-12"
    >
      <div className="sleeve">
        <Skeleton shape="block" className="aspect-square w-full rounded-cover-lg" />
      </div>
      <div className="flex flex-1 flex-col gap-4">
        <Skeleton className="h-12 w-2/3 lg:h-16" />
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-4 w-1/4" />
        <Skeleton shape="block" className="mt-8 h-12 w-1/2" />
      </div>
    </div>
  )
}
