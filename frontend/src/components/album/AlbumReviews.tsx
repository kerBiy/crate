import { toStars, useAlbumReviews } from '../../api/queries.ts'
import type { ReviewWithAuthor } from '../../api/types.ts'
import { Button } from '../ui/Button.tsx'
import { ErrorState } from '../ui/States.tsx'
import { Reviews, type Review } from './Reviews.tsx'

/** The album's reviews from review-service, newest first, with "Show more" for the next page. */
export function AlbumReviews({ albumId }: { albumId: string }) {
  const reviews = useAlbumReviews(albumId)

  if (reviews.isPending) return <Reviews reviews={[]} state="loading" onRetry={() => {}} />
  if (reviews.isError && !reviews.data) {
    return <Reviews reviews={[]} state="error" onRetry={() => reviews.refetch()} />
  }

  const items = reviews.data.pages.flatMap((page) => page.items).map(toView)
  const footer = reviews.isFetchNextPageError ? (
    <ErrorState
      message="Couldn't load more reviews."
      onRetry={() => reviews.fetchNextPage()}
      retrying={reviews.isFetchingNextPage}
    />
  ) : reviews.hasNextPage ? (
    <div>
      <Button onClick={() => reviews.fetchNextPage()} loading={reviews.isFetchingNextPage}>
        Show more reviews
      </Button>
    </div>
  ) : null

  return (
    <Reviews reviews={items} state={items.length ? 'ready' : 'empty'} onRetry={() => reviews.refetch()} footer={footer} />
  )
}

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })

export function toView(review: ReviewWithAuthor): Review {
  return {
    id: review.id,
    // An account that no longer exists keeps its reviews, without a name.
    name: review.author?.displayName || review.author?.username || 'Someone',
    rating: toStars(review.rating),
    text: review.body,
    when: dateFormat.format(new Date(review.createdAt)),
  }
}
