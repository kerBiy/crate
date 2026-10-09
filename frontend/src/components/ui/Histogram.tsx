import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { StarGlyph } from './RatingStars.tsx'

/** 0.5 → "½ star", 1 → "1 star", 4.5 → "4½ stars". */
export function formatStars(value: number) {
  const whole = Math.floor(value)
  const half = value % 1 !== 0 ? '½' : ''
  return `${whole || ''}${half} star${value > 1 ? 's' : ''}`
}

/**
 * Ratings spread in half-star buckets. Each bar is a button: hover (mouse), focus (keyboard)
 * or tap (touch) shows how many people gave that rating. The chart is one Tab stop; arrow keys,
 * Home and End move between bars.
 * counts: ratings per half-star bucket, 10 entries for 0.5, 1, 1.5 … 5.
 * size lg: the album page's ratings summary, taller bars with the scale's ends (½ star, 5 stars) under them.
 */
export function Histogram({ counts, size = 'md' }: { counts: number[]; size?: 'md' | 'lg' }) {
  const [active, setActive] = useState<number | null>(null)
  const root = useRef<HTMLDivElement>(null)
  const total = counts.reduce((sum, count) => sum + count, 0)
  const max = Math.max(1, ...counts)
  // The bar that takes Tab focus: the last one visited, else the tallest.
  const [stop, setStop] = useState(() => counts.indexOf(Math.max(...counts)))

  // A tap outside the chart closes the readout on touch screens.
  useEffect(() => {
    if (active === null) return
    function onPointer(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setActive(null)
    }
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [active])

  function describe(index: number) {
    const count = counts[index]
    const percent = total ? Math.round((count / total) * 100) : 0
    return `${formatStars((index + 1) / 2)}, ${count} rating${count === 1 ? '' : 's'} (${percent}%)`
  }

  function onKeyDown(event: KeyboardEvent, index: number) {
    const last = counts.length - 1
    const moves: Record<string, number> = {
      ArrowLeft: Math.max(0, index - 1),
      ArrowRight: Math.min(last, index + 1),
      Home: 0,
      End: last,
    }
    if (event.key === 'Escape') setActive(null)
    if (!(event.key in moves)) return
    event.preventDefault()
    root.current?.querySelectorAll('button')[moves[event.key]]?.focus()
  }

  return (
    <div ref={root} role="group" aria-label={`Ratings spread, ${total} rating${total === 1 ? '' : 's'}`} className="relative">
      {active !== null && <Readout index={active} text={describe(active)} />}
      <div className={`flex items-end ${size === 'lg' ? 'h-20' : 'h-12'}`}>
        {counts.map((count, i) => (
          <button
            key={i}
            type="button"
            aria-label={describe(i)}
            tabIndex={i === stop ? 0 : -1}
            onPointerEnter={(event) => event.pointerType === 'mouse' && setActive(i)}
            onPointerLeave={(event) => event.pointerType === 'mouse' && setActive(null)}
            onFocus={() => {
              setActive(i)
              setStop(i)
            }}
            onBlur={() => setActive(null)}
            onClick={() => setActive(i)}
            onKeyDown={(event) => onKeyDown(event, i)}
            className="flex h-full flex-1 cursor-default items-end rounded-cover px-hairline"
          >
            <span
              className={`w-full rounded-t-cover ${
                i === active ? 'bg-accent' : count === max ? 'bg-text' : 'bg-muted'
              }`}
              style={{ height: `${Math.max(6, (count / max) * 100)}%` }}
            />
          </button>
        ))}
      </div>
      {size === 'lg' && (
        // The scale's ends, in muted: labels, not ratings.
        <div aria-hidden="true" className="mt-2 flex justify-between text-meta">
          <StarGlyph fill={0.5} muted />
          <span className="inline-flex gap-hairline">
            {[0, 1, 2, 3, 4].map((i) => (
              <StarGlyph key={i} fill={1} muted />
            ))}
          </span>
        </div>
      )}
    </div>
  )
}

/** Floating label above the active bar, kept inside the chart at both ends. */
function Readout({ index, text }: { index: number; text: string }) {
  const edge = index <= 1 ? 'left-0' : index >= 8 ? 'right-0' : '-translate-x-1/2'
  const left = index > 1 && index < 8 ? { left: `${((index + 0.5) / 10) * 100}%` } : undefined
  return (
    <p
      aria-hidden="true"
      style={left}
      className={`pointer-events-none absolute bottom-full z-20 mb-2 rounded-control border border-border bg-surface-raised px-3 py-2 text-meta whitespace-nowrap text-text shadow-float ${edge}`}
    >
      {text}
    </p>
  )
}
