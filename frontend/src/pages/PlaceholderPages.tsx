import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { buttonStyles } from '../components/ui/Button.tsx'
import { EmptyState } from '../components/ui/States.tsx'

// Pages without data of their own, inside the app shell.

function Page({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-content flex-col items-start gap-4 px-4 py-12 lg:px-6">
      <h1 className="font-display text-h3 font-semibold text-text">{title}</h1>
      {children}
    </main>
  )
}

export function NotFoundPage() {
  return (
    <Page title="Not found">
      <EmptyState
        message="No records in this crate. The link may be old or mistyped."
        action={
          <Link to="/feed" className={buttonStyles('primary')}>
            Go to feed
          </Link>
        }
      />
    </Page>
  )
}
