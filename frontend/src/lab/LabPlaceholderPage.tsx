import { Link } from 'react-router'
import { buttonStyles } from '../components/ui/Button.tsx'
import { EmptyState } from '../components/ui/States.tsx'

const copy = {
  feed: {
    title: 'Feed',
    line: 'Nothing spinning yet. Follow a friend to fill this crate.',
    action: 'Find friends',
  },
  profile: {
    title: 'Profile',
    line: 'Your four favorites and ratings will live here.',
    action: 'Rate an album',
  },
}

/** Stand-ins so the navigation can be tried; the real screens come in Phase 1. */
export function LabPlaceholderPage({ page }: { page: keyof typeof copy }) {
  const { title, line, action } = copy[page]
  return (
    <main className="mx-auto flex max-w-content flex-col items-start gap-4 px-4 py-12 lg:px-6">
      <h1 className="font-display text-h3 font-semibold text-text">{title}</h1>
      <EmptyState
        message={line}
        action={
          <Link to="/lab/grid" className={buttonStyles('primary')}>
            {action}
          </Link>
        }
      />
    </main>
  )
}
