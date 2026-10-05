import { Star } from '@phosphor-icons/react'

type StarGlyphProps = { fill: number }

/** One star: a muted outline with an accent fill clipped to 0, half or full. */
export function StarGlyph({ fill }: StarGlyphProps) {
  return (
    <span className="relative inline-flex">
      <Star size="1em" className="text-muted" />
      {fill > 0 && (
        <span className={`absolute inset-0 overflow-hidden ${fill < 1 ? 'w-1/2' : 'w-full'}`}>
          <Star size="1em" weight="fill" className="text-accent" />
        </span>
      )}
    </span>
  )
}

export function starFill(value: number, index: number) {
  return Math.max(0, Math.min(1, value - index))
}

type StarsProps = { value: number; className?: string }

/** Read-only rating, sized by the surrounding font size. */
export function Stars({ value, className = '' }: StarsProps) {
  return (
    <span role="img" aria-label={`${value} out of 5 stars`} className={`inline-flex gap-px ${className}`}>
      {[0, 1, 2, 3, 4].map((i) => (
        <StarGlyph key={i} fill={starFill(value, i)} />
      ))}
    </span>
  )
}
