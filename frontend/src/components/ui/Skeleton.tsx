type SkeletonProps = {
  /** line: text, block: covers and controls, circle: avatars. Size it with className. */
  shape?: 'line' | 'block' | 'circle'
  className?: string
}

const shapes = { line: 'rounded-cover', block: 'rounded-cover', circle: 'rounded-full' }

/** Static placeholder shaped like the content that is loading. No shimmer: nothing moves at rest. */
export function Skeleton({ shape = 'line', className = '' }: SkeletonProps) {
  return <span aria-hidden="true" className={`block shrink-0 bg-surface ${shapes[shape]} ${className}`} />
}
