import type { ReactNode } from 'react'
import { Avatar } from '../ui/Avatar.tsx'
import { RatingStars } from '../ui/RatingStars.tsx'
import { Skeleton } from '../ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../ui/States.tsx'

/** One review as shown: rating in stars, text optional (a rating alone is a review too). */
export type Review = { id?: string; name: string; rating: number; text: string | null; when: string }

export type DataState = 'ready' | 'loading' | 'empty' | 'error'

type ReviewsProps = {
  reviews: Review[]
  state: DataState
  onRetry: () => void
  emptyMessage?: string
  /** Under the list: loading the next page, for example. */
  footer?: ReactNode
}

/** Reviews as reading material: text one step up (lead), separators inset to the text column. */
export function Reviews({
  reviews,
  state,
  onRetry,
  emptyMessage = 'No reviews yet. Rate it and be the first to say why.',
  footer,
}: ReviewsProps) {
  if (state === 'loading') return <ReviewsSkeleton />
  if (state === 'error') return <ErrorState message="Couldn't load reviews." onRetry={onRetry} />
  if (state === 'empty' || reviews.length === 0) {
    return <EmptyState message={emptyMessage} />
  }

  return (
    <div className="flex flex-col gap-6">
      <ul className="rows flex flex-col gap-6">
        {reviews.map((review) => (
          <li key={review.id ?? review.name} className="flex gap-4">
            <Avatar name={review.name} />
            <div className="row-rule min-w-0 flex-1 pb-6">
              <p className="flex items-baseline justify-between gap-3">
                <span className="truncate text-body font-medium text-text">{review.name}</span>
                <span className="shrink-0 text-meta text-muted">{review.when}</span>
              </p>
              <RatingStars value={review.rating} className="mt-1 text-meta" />
              {review.text && <p className="mt-3 max-w-prose text-lead whitespace-pre-line text-text">{review.text}</p>}
            </div>
          </li>
        ))}
      </ul>
      {footer}
    </div>
  )
}

function ReviewsSkeleton() {
  return (
    // The real list's geometry (rows, inset rules), so nothing moves when the reviews arrive.
    <ul aria-busy="true" aria-label="Loading reviews" className="rows flex flex-col gap-6">
      {[0, 1, 2].map((i) => (
        <li key={i} className="flex gap-4">
          <Skeleton shape="circle" className="size-avatar" />
          <div className="row-rule flex min-w-0 flex-1 flex-col gap-2 pb-6">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/4" />
            <Skeleton className="mt-2 h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function SectionTitle({ children }: { children: string }) {
  return <h2 className="mb-4 font-display text-h4 font-semibold text-text">{children}</h2>
}
