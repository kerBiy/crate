import { Link } from 'react-router'
import { buttonStyles } from '../components/ui/Button.tsx'
import { PageHeader, pageFrame } from '../components/ui/PageHeader.tsx'
import { EmptyState } from '../components/ui/States.tsx'

// Pages without data of their own, inside the app shell.

export function NotFoundPage() {
  return (
    <main className={pageFrame}>
      <PageHeader title="Not found" />
      <div className="mt-6 lg:mt-8">
        <EmptyState
          message="No records in this crate. The link may be old or mistyped."
          action={
            <Link to="/feed" className={buttonStyles('primary')}>
              Go to feed
            </Link>
          }
        />
      </div>
    </main>
  )
}
