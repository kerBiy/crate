import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useFollowList, useMe, useProfile, type FollowListKind } from '../api/queries.ts'
import type { Profile } from '../api/types.ts'
import { LoadMore } from '../components/LoadMore.tsx'
import { buttonStyles } from '../components/ui/Button.tsx'
import { PageHeader } from '../components/ui/PageHeader.tsx'
import { PersonRow, PersonRowSkeleton } from '../components/ui/PersonRow.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { nameOf } from '../format.ts'

const page = 'mx-auto flex max-w-content flex-col px-4 pt-6 pb-16 lg:px-6 lg:pt-12'
const body = 'mt-6 lg:mt-8'
// Back to the person whose list this is: small, above the large title, as tall as a tap.
const back = 'flex min-h-tap items-center self-start rounded-control text-body text-muted'
const titles: Record<FollowListKind, string> = { followers: 'Followers', following: 'Following' }

/** /u/:username/followers and /following: the people, newest follow first. */
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const { username = '' } = useParams()
  const profile = useProfile(username)

  if (profile.isPending) {
    return (
      <main aria-busy="true" aria-label="Loading" className={page}>
        <div className="flex min-h-tap items-center">
          <Skeleton className="h-3 w-1/4 lg:w-1/12" />
        </div>
        <Skeleton className="h-12 w-1/2 lg:h-16 lg:w-1/4" />
        <div className={body}>
          <PeopleSkeleton />
        </div>
      </main>
    )
  }
  if (profile.isError) {
    const missing = profile.error instanceof ApiError && profile.error.status === 404
    return (
      <main className={page}>
        <PageHeader title={titles[kind]} />
        <div className={body}>
          {missing ? (
            <EmptyState message="No one by that name. The link may be old or mistyped." />
          ) : (
            <ErrorState message="Couldn't load this list." onRetry={() => profile.refetch()} retrying={profile.isFetching} />
          )}
        </div>
      </main>
    )
  }

  return (
    <main className={page}>
      <header className="flex flex-col">
        <Link to={`/u/${encodeURIComponent(profile.data.username)}`} className={back}>
          {nameOf(profile.data)}
        </Link>
        <PageHeader title={titles[kind]} />
      </header>
      <div className={body}>
        <People profile={profile.data} kind={kind} />
      </div>
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
    <div className="flex flex-col gap-8">
      <ul className="rows flex max-w-prose flex-col">
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
    <ul aria-busy="true" aria-label="Loading people" className="rows flex max-w-prose flex-col">
      {Array.from({ length: 5 }, (_, i) => (
        <li key={i}>
          <PersonRowSkeleton />
        </li>
      ))}
    </ul>
  )
}
