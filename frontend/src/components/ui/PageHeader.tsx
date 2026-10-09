import type { ReactNode } from 'react'

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
