import type { ReactNode } from 'react'
import { Button } from './Button.tsx'

type EmptyStateProps = {
  /** One short line; playful is fine here. */
  message: string
  /** The one thing to do next: a Button or a link styled as one. */
  action?: ReactNode
}

export function EmptyState({ message, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-start gap-4">
      <p className="max-w-prose text-muted">{message}</p>
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
