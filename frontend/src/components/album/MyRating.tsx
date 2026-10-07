import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client.ts'
import { toRating, toStars, useDeleteReview, useMyReview, useSaveReview } from '../../api/queries.ts'
import { Button } from '../ui/Button.tsx'
import { RatingInput } from '../ui/RatingInput.tsx'
import { Textarea } from '../ui/Textarea.tsx'

const maxReviewLength = 5000

type MyRatingProps = {
  albumId: string
  title: string
  /** Confirms what happened, or says what failed. */
  notify: (message: string) => void
  /** The record's one slow spin when a rating is given. */
  onRated: () => void
}

/** Rate, change or remove my rating, and write the optional review text that goes with it. */
export function MyRating({ albumId, title, notify, onRated }: MyRatingProps) {
  const mine = useMyReview(albumId)
  const save = useSaveReview(albumId)
  const remove = useDeleteReview(albumId)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  // When the editor closes, keyboard focus goes back to the button that opened it.
  const editButton = useRef<HTMLButtonElement>(null)
  const wasEditing = useRef(false)

  useEffect(() => {
    if (wasEditing.current && !editing) editButton.current?.focus()
    wasEditing.current = editing
  }, [editing])

  if (mine.isError) {
    return (
      <div role="alert" className="flex flex-wrap items-center gap-3">
        <p className="text-text">Couldn't load your rating.</p>
        <Button variant="ghost" onClick={() => mine.refetch()} loading={mine.isFetching}>
          Try again
        </Button>
      </div>
    )
  }

  const review = mine.data ?? null
  const stars = review ? toStars(review.rating) : 0

  function rate(value: number) {
    onRated()
    save.mutate(
      { rating: toRating(value), body: review?.body ?? null },
      { onError: (error) => notify(saveFailed(error, 'rating')) },
    )
  }

  function removeRating() {
    setEditing(false)
    remove.mutate(undefined, {
      onSuccess: () => notify('Rating removed'),
      onError: () => notify("Couldn't remove your rating. Try again."),
    })
  }

  function startEditing() {
    setDraft(review?.body ?? '')
    setEditing(true)
  }

  function saveReview(event: FormEvent) {
    event.preventDefault()
    if (!review) return
    const body = draft.trim() || null
    save.mutate(
      { rating: review.rating, body },
      {
        onSuccess: () => {
          setEditing(false)
          notify(body ? 'Review saved' : 'Review removed')
        },
        onError: (error) => notify(saveFailed(error, 'review')),
      },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <RatingInput label={`Rate ${title}`} value={stars} onRate={rate} disabled={mine.isPending} />
        <span className="text-meta text-muted">{mine.isPending ? '' : review ? 'Rated' : 'Rate'}</span>
      </div>

      {review && !editing && (
        <div className="-mx-4 flex flex-wrap">
          <Button ref={editButton} variant="ghost" onClick={startEditing}>
            {review.body ? 'Edit review' : 'Write a review'}
          </Button>
          <Button variant="ghost" onClick={removeRating} loading={remove.isPending}>
            Remove rating
          </Button>
        </div>
      )}

      {review && editing && (
        <form
          onSubmit={saveReview}
          onKeyDown={(event) => event.key === 'Escape' && setEditing(false)}
          className="flex max-w-prose flex-col gap-3"
        >
          <Textarea
            label="Your review"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            maxLength={maxReviewLength}
            hint={draft.length > maxReviewLength - 500 ? `${maxReviewLength - draft.length} characters left` : undefined}
            autoFocus
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" variant="primary" loading={save.isPending}>
              Save review
            </Button>
            <Button variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

/** 503: catalog couldn't confirm the album exists, so nothing was saved. Plain, never cute. */
function saveFailed(error: unknown, what: 'rating' | 'review') {
  if (error instanceof ApiError && error.status === 503) {
    return "Can't check this album right now. Try again in a moment."
  }
  return `Couldn't save your ${what}. Try again.`
}
