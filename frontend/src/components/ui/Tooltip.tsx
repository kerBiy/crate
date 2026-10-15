import { useState, type ReactNode } from 'react'

/**
 * A short label that names an icon-only control, shown under it on hover (pointer devices only) and on
 * keyboard focus, never on a tap. Escape hides it until the pointer or focus leaves. The text is
 * aria-hidden: the control must carry the same name itself, so screen readers hear it once.
 */
export function Tooltip({ text, children }: { text: string; children: ReactNode }) {
  const [dismissed, setDismissed] = useState(false)
  return (
    <span
      className="tooltip-host"
      data-dismissed={dismissed || undefined}
      onKeyDown={(event) => {
        if (event.key === 'Escape') setDismissed(true)
      }}
      onBlur={() => setDismissed(false)}
      onPointerLeave={() => setDismissed(false)}
    >
      {children}
      <span
        aria-hidden="true"
        className="tooltip rounded-control border border-border bg-surface-raised px-2 py-1 text-meta whitespace-nowrap text-text shadow-float"
      >
        {text}
      </span>
    </span>
  )
}
