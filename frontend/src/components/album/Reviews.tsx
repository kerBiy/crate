import type { Review } from '../../lab/albums.ts'
import { Avatar } from '../ui/Avatar.tsx'
import { RatingStars } from '../ui/RatingStars.tsx'
import { Skeleton } from '../ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../ui/States.tsx'

export type DataState = 'ready' | 'loading' | 'empty' | 'error'

type ReviewsProps = {
  reviews: Review[]
  state: DataState
  onRetry: () => void
}

export function Reviews({ reviews, state, onRetry }: ReviewsProps) {
  if (state === 'loading') return <ReviewsSkeleton />
  if (state === 'error') return <ErrorState message="Couldn't load reviews." onRetry={onRetry} />
  if (state === 'empty' || reviews.length === 0) {
    return <EmptyState message="No reviews yet. Rate it and be the first to say why." />
  }

  return (
    <ul className="flex flex-col divide-y divide-border">
      {reviews.map((review) => (
        <li key={review.name} className="flex gap-3 py-4 first:pt-0">
          <Avatar name={review.name} />
          <div className="flex min-w-0 flex-col gap-1">
            <p className="flex flex-wrap items-center gap-x-3 text-meta">
              <span className="font-medium text-text">{review.name}</span>
              <RatingStars value={review.rating} />
              <span className="text-muted">{review.when}</span>
            </p>
            <p className="max-w-prose text-text">{review.text}</p>
          </div>
        </li>
      ))}
    </ul>
  )
}

function ReviewsSkeleton() {
  return (
    <ul aria-busy="true" aria-label="Loading reviews" className="flex flex-col gap-8">
      {[0, 1, 2].map((i) => (
        <li key={i} className="flex gap-3">
          <Skeleton shape="circle" className="size-avatar" />
          <div className="flex w-full max-w-prose flex-col gap-2">
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-2/3" />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function SectionTitle({ children }: { children: string }) {
  return <h2 className="mb-4 font-display text-h4 font-semibold text-text">{children}</h2>
}
