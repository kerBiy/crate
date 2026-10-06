import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useMe } from '../api/queries.ts'
import { logout } from '../api/queryClient.ts'
import { Button, buttonStyles } from '../components/ui/Button.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'

// Stand-ins inside the real app shell until the feed and profile have a backend.

function Page({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-content flex-col items-start gap-4 px-4 py-12 lg:px-6">
      <h1 className="font-display text-h3 font-semibold text-text">{title}</h1>
      {children}
    </main>
  )
}

export function FeedPage() {
  return (
    <Page title="Feed">
      <EmptyState
        message="Nothing spinning yet. Follow a friend to fill this crate."
        action={
          <Link to="/search" className={buttonStyles('primary')}>
            Search albums
          </Link>
        }
      />
    </Page>
  )
}

export function ProfilePage() {
  const me = useMe()

  if (me.isPending) {
    return (
      <main aria-busy="true" aria-label="Loading profile" className="mx-auto flex max-w-content flex-col gap-4 px-4 py-12 lg:px-6">
        <Skeleton className="h-8 w-1/2 lg:w-1/4" />
        <Skeleton className="h-4 w-2/3 lg:w-1/3" />
      </main>
    )
  }
  if (me.isError) {
    return (
      <Page title="Profile">
        <ErrorState message="Couldn't load your profile." onRetry={() => me.refetch()} retrying={me.isFetching} />
      </Page>
    )
  }

  return (
    <Page title={me.data.displayName ?? me.data.username}>
      <EmptyState message="Your four favorites and ratings will live here." />
      <Button variant="ghost" onClick={logout} className="-mx-4">
        Log out
      </Button>
    </Page>
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
