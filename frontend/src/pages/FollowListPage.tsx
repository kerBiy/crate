import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useFollowList, useMe, useProfile, type FollowListKind } from '../api/queries.ts'
import type { Profile } from '../api/types.ts'
import { LoadMore } from '../components/LoadMore.tsx'
import { buttonStyles } from '../components/ui/Button.tsx'
import { PersonRow, PersonRowSkeleton } from '../components/ui/PersonRow.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { nameOf } from '../format.ts'

const page = 'mx-auto flex max-w-content flex-col gap-8 px-4 py-8 lg:px-6 lg:py-12'
const titles: Record<FollowListKind, string> = { followers: 'Followers', following: 'Following' }

/** /u/:username/followers and /following: the people, newest follow first. */
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const { username = '' } = useParams()
  const profile = useProfile(username)

  if (profile.isPending) {
    return (
      <main aria-busy="true" aria-label="Loading" className={page}>
        <Skeleton className="h-8 w-1/2 lg:w-1/4" />
        <PeopleSkeleton />
      </main>
    )
  }
  if (profile.isError) {
    const missing = profile.error instanceof ApiError && profile.error.status === 404
    return (
      <main className={page}>
        <h1 className="font-display text-h3 font-semibold text-text">{titles[kind]}</h1>
        {missing ? (
          <EmptyState message="No one by that name. The link may be old or mistyped." />
        ) : (
          <ErrorState message="Couldn't load this list." onRetry={() => profile.refetch()} retrying={profile.isFetching} />
        )}
      </main>
    )
  }

  return (
    <main className={page}>
      <header className="flex flex-col">
        <Link to={`/u/${encodeURIComponent(profile.data.username)}`} className="flex min-h-tap items-center self-start rounded-control text-meta text-muted">
          {nameOf(profile.data)}
        </Link>
        <h1 className="font-display text-h3 font-semibold text-text">{titles[kind]}</h1>
      </header>
      <People profile={profile.data} kind={kind} />
    </main>
  )
}

function People({ profile, kind }: { profile: Profile; kind: FollowListKind }) {
  const list = useFollowList(profile.id, kind)
  const me = useMe()
  const mine = me.data?.id === profile.id

  if (list.isPending) return <PeopleSkeleton />
  if (list.isError && !list.data) {
    return <ErrorState message="Couldn't load this list." onRetry={() => list.refetch()} retrying={list.isFetching} />
  }

  const people = list.data.pages.flatMap((p) => p.items)
  if (!people.length) {
    if (kind === 'followers') return <EmptyState message="No followers yet." />
    return mine ? (
      <EmptyState
        message="You don't follow anyone yet."
        action={
          <Link to="/search?type=people" className={buttonStyles('primary')}>
            Search people
          </Link>
        }
      />
    ) : (
      <EmptyState message="Not following anyone yet." />
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex max-w-prose flex-col divide-y divide-border">
        {people.map((person) => (
          <li key={person.id}>
            <PersonRow username={person.username} displayName={person.displayName} />
          </li>
        ))}
      </ul>
      <LoadMore
        hasMore={list.hasNextPage}
        loading={list.isFetchingNextPage}
        failed={list.isFetchNextPageError}
        onLoad={() => list.fetchNextPage()}
        error="Couldn't load more people."
      />
    </div>
  )
}

export function PeopleSkeleton() {
  return (
    <ul aria-busy="true" aria-label="Loading people" className="flex max-w-prose flex-col">
      {Array.from({ length: 5 }, (_, i) => (
        <li key={i}>
          <PersonRowSkeleton />
        </li>
      ))}
    </ul>
  )
}
