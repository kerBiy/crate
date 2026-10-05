import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { buttonStyles } from '../components/ui/Button.tsx'
import { EmptyState } from '../components/ui/States.tsx'

// Phase 0 stand-ins inside the real app shell. Each screen arrives in Phase 1 (DESIGN.md section 13).

function Placeholder({ title, message, action }: { title: string; message: string; action?: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-content flex-col items-start gap-4 px-4 py-12 lg:px-6">
      <h1 className="font-display text-h3 font-semibold text-text">{title}</h1>
      <EmptyState message={message} action={action} />
    </main>
  )
}

export function FeedPage() {
  return <Placeholder title="Feed" message="Nothing spinning yet. Follow a friend to fill this crate." />
}

export function SearchPage() {
  return <Placeholder title="Search" message="Search isn't open yet." />
}

export function ProfilePage() {
  return <Placeholder title="Profile" message="Your four favorites and ratings will live here." />
}

export function NotFoundPage() {
  return (
    <Placeholder
      title="Not found"
      message="No records in this crate. The link may be old or mistyped."
      action={
        <Link to="/feed" className={buttonStyles('primary')}>
          Go to feed
        </Link>
      }
    />
  )
}
