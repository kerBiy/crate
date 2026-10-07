type SkeletonProps = {
  /**
   * line: a bar on its own row. text: a bar inside a line of text, so the text's line-height
   * sets the row height and nothing shifts when the real text arrives. block: covers and
   * controls. circle: avatars. Size it with className.
   */
  shape?: 'line' | 'text' | 'block' | 'circle'
  className?: string
}

const shapes = {
  line: 'block rounded-cover',
  text: 'inline-block h-3 rounded-cover align-middle',
  block: 'block rounded-cover',
  circle: 'block rounded-full',
}

/** Placeholder shaped like the content that is loading. It pulses in place (static with reduced motion). */
export function Skeleton({ shape = 'line', className = '' }: SkeletonProps) {
  return <span aria-hidden="true" className={`skeleton shrink-0 ${shapes[shape]} ${className}`} />
}
