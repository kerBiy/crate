import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client.ts'
import { toRating, toStars, useMyReview, useRemoveReview, useSaveReview } from '../../api/queries.ts'
import { Button } from '../ui/Button.tsx'
import { RatingInput } from '../ui/RatingInput.tsx'
import { Skeleton } from '../ui/Skeleton.tsx'
import { Textarea } from '../ui/Textarea.tsx'
import type { Toast } from '../ui/Toast.tsx'

const maxReviewLength = 5000

type MyRatingProps = {
  albumId: string
  title: string
  /** Confirms what happened, or says what failed; carries the Undo for a removed rating. */
  toast: Toast
  /** The record's one slow spin when a rating is given. */
  onRated: () => void
  /** lg: the album page's own strip: bigger stars, the label in body size. */
  size?: 'md' | 'lg'
}

/**
 * Rate, change or remove my rating, and write the optional review text that goes with it.
 * The page's main action: a label says what the stars are, and their actions sit right under them.
 */
export function MyRating({ albumId, title, toast, onRated, size = 'md' }: MyRatingProps) {
  const notify = toast.show
  const mine = useMyReview(albumId)
  const save = useSaveReview(albumId)
  const removal = useRemoveReview(albumId, () => notify("Couldn't remove your rating. Try again."))
  const starsRef = useRef<HTMLDivElement>(null)
  const [editing, setEditing] = useState(false)
  // Unsaved review text. Escape closes the editor but keeps it; only Cancel, saving or removing drops it.
  const [draft, setDraft] = useState<string | null>(null)
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
  const left = maxReviewLength - (draft?.length ?? 0)

  function rate(value: number) {
    // Rating again during the Undo window replaces the removal: the server still has the row.
    removal.drop()
    toast.dismiss()
    onRated()
    save.mutate(
      { rating: toRating(value), body: review?.body ?? null },
      { onError: (error) => notify(saveFailed(error, 'rating')) },
    )
  }

  /** Focus was on the toast's Undo (or nowhere, once it closed): bring it back to the stars. */
  function refocus() {
    const focused = document.activeElement
    if (!focused || focused === document.body || focused.closest('.toast')) starsRef.current?.focus()
  }

  /** Gone from the screen at once; the DELETE waits for the Undo window (see useRemoveReview). */
  function removeRating() {
    setEditing(false)
    setDraft(null)
    removal.remove()
    notify('Rating removed', {
      // "Remove rating" has just disappeared, so focus goes to Undo instead of being lost.
      action: {
        label: 'Undo',
        focus: true,
        run: () => {
          removal.undo()
          refocus()
        },
      },
      onClose: () => {
        removal.commit()
        refocus()
      },
    })
  }

  function startEditing() {
    setDraft((kept) => kept ?? review?.body ?? '')
    setEditing(true)
  }

  function cancelEditing() {
    setEditing(false)
    setDraft(null)
  }

  function saveReview(event: FormEvent) {
    event.preventDefault()
    if (!review) return
    const body = draft?.trim() || null
    save.mutate(
      { rating: review.rating, body },
      {
        onSuccess: () => {
          setEditing(false)
          setDraft(null)
          notify(body ? 'Review saved' : 'Review removed')
        },
        onError: (error) => notify(saveFailed(error, 'review')),
      },
    )
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <p className={size === 'lg' ? 'text-body font-medium text-text' : 'text-meta text-muted'}>
        {mine.isPending ? <Skeleton shape="text" className="w-16" /> : review ? 'Your rating' : 'Rate this album'}
      </p>
      <RatingInput ref={starsRef} label={`Rate ${title}`} value={stars} onRate={rate} disabled={mine.isPending} size={size} />

      {review && !editing && (
        <div className="-mx-4 flex flex-wrap">
          <Button ref={editButton} variant="ghost" onClick={startEditing}>
            {review.body || draft !== null ? 'Edit review' : 'Write a review'}
          </Button>
          <Button variant="ghost" onClick={removeRating} loading={removal.removing}>
            Remove rating
          </Button>
        </div>
      )}

      {review && editing && (
        <form
          onSubmit={saveReview}
          onKeyDown={(event) => event.key === 'Escape' && setEditing(false)}
          className="mt-2 flex w-full max-w-prose flex-col gap-3"
        >
          <Textarea
            label="Your review"
            value={draft ?? ''}
            onChange={(event) => setDraft(event.target.value)}
            maxLength={maxReviewLength}
            hint={left < 500 ? `${left} characters left` : undefined}
            autoFocus
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" variant="primary" loading={save.isPending}>
              Save review
            </Button>
            <Button variant="ghost" onClick={cancelEditing}>
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
