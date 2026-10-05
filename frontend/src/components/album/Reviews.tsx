import type { Review } from '../../lab/albums.ts'
import { Avatar, Button } from './bits.tsx'
import { Stars } from './Stars.tsx'

export type DataState = 'ready' | 'loading' | 'empty' | 'error'

type ReviewsProps = {
  reviews: Review[]
  state: DataState
  onRetry: () => void
}

export function Reviews({ reviews, state, onRetry }: ReviewsProps) {
  if (state === 'loading') return <ReviewsSkeleton />
  if (state === 'error') {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-text">Couldn't load reviews.</p>
        <Button onClick={onRetry}>Try again</Button>
      </div>
    )
  }
  if (state === 'empty' || reviews.length === 0) {
    return <p className="text-muted">No reviews yet. Rate it and be the first to say why.</p>
  }

  return (
    <ul className="flex flex-col divide-y divide-border">
      {reviews.map((review) => (
        <li key={review.name} className="flex gap-3 py-4 first:pt-0">
          <Avatar name={review.name} />
          <div className="flex min-w-0 flex-col gap-1">
            <p className="flex flex-wrap items-center gap-x-3 text-meta">
              <span className="font-medium text-text">{review.name}</span>
              <Stars value={review.rating} />
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
          <span className="skeleton size-avatar shrink-0 rounded-full" />
          <div className="flex w-full max-w-prose flex-col gap-2">
            <span className="skeleton h-3 w-1/3 rounded-cover" />
            <span className="skeleton h-3 w-full rounded-cover" />
            <span className="skeleton h-3 w-2/3 rounded-cover" />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function SectionTitle({ children }: { children: string }) {
  return <h2 className="mb-4 font-display text-h4 font-semibold text-text">{children}</h2>
}
