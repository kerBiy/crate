import { useState, type CSSProperties, type KeyboardEvent, type MouseEvent } from 'react'
import { StarGlyph, starFill } from './RatingStars.tsx'

type RatingInputProps = {
  label: string
  value: number
  onRate: (value: number) => void
  disabled?: boolean
}

/**
 * Star rating as a slider: arrow keys move in half stars, Enter confirms.
 * Pointer: the left half of a star gives a half star, the right half a full one.
 */
export function RatingInput({ label, value, onRate, disabled = false }: RatingInputProps) {
  const [pending, setPending] = useState<number | null>(null)
  const [popCount, setPopCount] = useState(0)
  const shown = pending ?? value

  function confirm(next: number) {
    setPending(null)
    if (next === 0) return
    onRate(next)
    setPopCount((count) => count + 1)
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

  function onStarClick(event: MouseEvent<HTMLSpanElement>, index: number) {
    const box = event.currentTarget.getBoundingClientRect()
    const leftHalf = event.clientX - box.left < box.width / 2
    confirm(index + (leftHalf ? 0.5 : 1))
  }

  return (
    <div
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
      className={`-mx-2 inline-flex rounded-control text-h3 ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
    >
      {[0, 1, 2, 3, 4].map((i) => {
        const fill = starFill(shown, i)
        const pop = popCount > 0 && fill > 0
        return (
          <span
            key={`${i}-${popCount}`}
            onClick={disabled ? undefined : (event) => onStarClick(event, i)}
            className="flex size-tap items-center justify-center"
          >
            <span className={pop ? 'star-pop inline-flex' : 'inline-flex'} style={{ '--i': i } as CSSProperties}>
              <StarGlyph fill={fill} muted={disabled} />
            </span>
          </span>
        )
      })}
    </div>
  )
}
