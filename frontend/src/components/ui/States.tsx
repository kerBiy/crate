import type { Icon } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import { Button } from './Button.tsx'

type EmptyStateProps = {
  /** One short line; playful is fine here. */
  message: string
  /** The one thing to do next: a Button or a link styled as one. */
  action?: ReactNode
  /** Names the place, in muted, above the line: a record for an empty crate, a magnifier for no results. */
  icon?: Icon
}

/** The line is the view's content now, not a footnote: lead size, in text. */
export function EmptyState({ message, action, icon: Glyph }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-start gap-4">
      {Glyph && <Glyph size="2em" aria-hidden="true" className="text-h3 text-muted" />}
      <p className="max-w-prose text-lead text-text">{message}</p>
      {action}
    </div>
  )
}

type ErrorStateProps = {
  /** What failed, plainly: "Couldn't load reviews." */
  message: string
  onRetry: () => void
  /** True while the retry is in flight. */
  retrying?: boolean
}

export function ErrorState({ message, onRetry, retrying = false }: ErrorStateProps) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3">
      <p className="max-w-prose text-text">{message}</p>
      <Button onClick={onRetry} loading={retrying}>
        Try again
      </Button>
    </div>
  )
}
