import { useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type Ref } from 'react'
import { StarGlyph, starFill } from './RatingStars.tsx'

type RatingInputProps = {
  label: string
  value: number
  onRate: (value: number) => void
  disabled?: boolean
  ref?: Ref<HTMLDivElement>
  /** lg: the album page, where rating is the main action. Same 44px cells, bigger stars. */
  size?: 'md' | 'lg'
}

/**
 * Star rating as a slider, in half stars.
 * Pointer: hovering previews, pressing and dragging scrubs under the finger, releasing rates. The
 * left half of a star is a half star, the right half a full one. A vertical drag on touch is a
 * scroll, not a rating: the browser takes it (pan-y) and the preview goes back.
 * Keyboard: arrow keys move, Enter confirms, Escape puts the saved value back.
 */
export function RatingInput({ label, value, onRate, disabled = false, ref, size = 'md' }: RatingInputProps) {
  const [pending, setPending] = useState<number | null>(null)
  // The last confirmed rating: its stars pop once. Keyed by count so the same value can pop again.
  const [popped, setPopped] = useState({ count: 0, value: 0 })
  // Just rated, until `value` catches up: the save updates it a tick later, and in between the stars
  // would flash back to the old rating for a frame.
  const [committed, setCommitted] = useState<number | null>(null)
  if (committed !== null && committed === value) setCommitted(null)
  const dragging = useRef(false)
  const shown = pending ?? committed ?? value

  function confirm(next: number) {
    setPending(null)
    if (next === 0) return
    if (next !== value) setCommitted(next)
    onRate(next)
    setPopped((last) => ({ count: last.count + 1, value: next }))
  }

  function onKeyDown(event: KeyboardEvent) {
    const step: Record<string, number> = { ArrowRight: 0.5, ArrowUp: 0.5, ArrowLeft: -0.5, ArrowDown: -0.5 }
    if (event.key in step) {
      event.preventDefault()
      setPending(Math.max(0.5, Math.min(5, shown + step[event.key])))
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      setPending(event.key === 'Home' ? 0.5 : 5)
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      confirm(shown)
    } else if (event.key === 'Escape') {
      setPending(null)
    }
  }

  /** The rating under the pointer: five equal stars, each split into halves. Clamped to the row. */
  function valueAt(event: PointerEvent<HTMLDivElement>) {
    const box = event.currentTarget.getBoundingClientRect()
    const star = box.width / 5
    const x = Math.min(Math.max(event.clientX - box.left, 0), box.width - 1)
    const index = Math.floor(x / star)
    return index + (x - index * star < star / 2 ? 0.5 : 1)
  }

  const pointer = {
    onPointerDown(event: PointerEvent<HTMLDivElement>) {
      if (event.button !== 0) return
      dragging.current = true
      // Keep getting moves when the finger slides past the ends of the row.
      event.currentTarget.setPointerCapture(event.pointerId)
      setPending(valueAt(event))
    },
    onPointerMove(event: PointerEvent<HTMLDivElement>) {
      if (dragging.current || event.pointerType === 'mouse') setPending(valueAt(event))
    },
    onPointerUp(event: PointerEvent<HTMLDivElement>) {
      if (!dragging.current) return
      dragging.current = false
      confirm(valueAt(event))
    },
    onPointerCancel() {
      dragging.current = false
      setPending(null)
    },
    onPointerLeave(event: PointerEvent<HTMLDivElement>) {
      if (!dragging.current && event.pointerType === 'mouse') setPending(null)
    },
  }

  return (
    <div
      ref={ref}
      role="slider"
      tabIndex={disabled ? -1 : 0}
      aria-label={label}
      aria-disabled={disabled || undefined}
      aria-valuemin={0}
      aria-valuemax={5}
      aria-valuenow={shown}
      aria-valuetext={shown === 0 ? 'Not rated' : `${shown} out of 5 stars`}
      onKeyDown={disabled ? undefined : onKeyDown}
      onBlur={() => setPending(null)}
      {...(disabled ? {} : pointer)}
      className={`-mx-2 inline-flex touch-pan-y rounded-control ${size === 'lg' ? 'text-h2' : 'text-h3'} select-none ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
    >
      {[0, 1, 2, 3, 4].map((i) => {
        const fill = starFill(shown, i)
        // From the confirmed value, not the shown one: hovering afterwards must not replay the pop.
        const pop = starFill(popped.value, i) > 0
        return (
          <span key={`${i}-${popped.count}`} className="flex size-tap items-center justify-center">
            <span className={pop ? 'star-pop inline-flex' : 'inline-flex'} style={{ '--i': i } as CSSProperties}>
              <StarGlyph fill={fill} muted={disabled} />
            </span>
          </span>
        )
      })}
    </div>
  )
}
