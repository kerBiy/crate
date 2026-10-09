import type { ReactNode } from 'react'

/**
 * The frame of a page's content: content width, side gutters, and the rhythm from the header
 * (24 below it on mobile, 48 on desktop; DESIGN.md section 4.4).
 */
export const pageFrame = 'mx-auto flex max-w-content flex-col px-4 pt-6 pb-16 lg:px-6 lg:pt-12'

type PageHeaderProps = {
  title: string
  /** A control that changes the whole page, at the end of the title's row (Search's Albums / People). */
  children?: ReactNode
}

/** Large title of a top-level page: it answers "where am I?" (DESIGN.md section 8). */
export function PageHeader({ title, children }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between gap-4">
      <h1 className="min-w-0 font-narrow font-display text-h1 font-semibold text-balance text-text lg:text-display">
        {title}
      </h1>
      {children}
    </div>
  )
}
