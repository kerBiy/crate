import { GearSix } from '@phosphor-icons/react'
import { Link, Navigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { toStars, useFollow, useMe, useProfile, useUserRatings } from '../api/queries.ts'
import type { Profile } from '../api/types.ts'
import { LoadMore } from '../components/LoadMore.tsx'
import { AlbumTile, AlbumTileSkeleton, albumGrid, eagerAlbumTiles } from '../components/ui/AlbumTile.tsx'
import { buttonStyles } from '../components/ui/Button.tsx'
import { FollowButton } from '../components/ui/FollowButton.tsx'
import { PageHeader, pageFrame } from '../components/ui/PageHeader.tsx'
import { SectionHeading } from '../components/ui/SectionHeading.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { ToastRegion, useToast } from '../components/ui/Toast.tsx'
import { count, nameOf } from '../format.ts'


/** /u/:username: who they are, follow counts, Follow, and the albums they rated. */
export function ProfilePage() {
  const { username = '' } = useParams()
  const profile = useProfile(username)
  const me = useMe()

  if (profile.isPending) return <ProfileSkeleton />
  if (profile.isError) {
    const missing = profile.error instanceof ApiError && profile.error.status === 404
    return (
      <main className={pageFrame}>
        <PageHeader title={missing ? 'Not found' : 'Profile'} />
        <div className="mt-6 lg:mt-8">
          {missing ? (
            <EmptyState
              message="No one by that name. The link may be old or mistyped."
              action={
                <Link to="/search?type=people" className={buttonStyles('primary')}>
                  Search people
                </Link>
              }
            />
          ) : (
            <ErrorState message="Couldn't load this profile." onRetry={() => profile.refetch()} retrying={profile.isFetching} />
          )}
        </div>
      </main>
    )
  }

  const person = profile.data
  const mine = me.data?.id === person.id
  return (
    <main className={pageFrame}>
      <ProfileHeader profile={person} mine={mine} />
      <section className="mt-12 flex flex-col lg:mt-16">
        <SectionHeading>Rated</SectionHeading>
        <Ratings userId={person.id} mine={mine} />
      </section>
    </main>
  )
}

/** The person's name as the page's large title, the username one step below, then counts and Follow on one row. */
function ProfileHeader({ profile, mine }: { profile: Profile; mine: boolean }) {
  const base = `/u/${encodeURIComponent(profile.username)}`
  return (
    <header className="flex flex-col">
      <h1 className="break-words font-narrow font-display text-h1 font-semibold text-balance text-text lg:text-display">
        {nameOf(profile)}
      </h1>
      {profile.displayName && <p className="mt-2 font-display text-h4 text-muted lg:mt-3">{profile.username}</p>}
      {/* Reading width, like the lists: on desktop the action stays next to the counts, not across the page. */}
      <div className="mt-4 flex max-w-prose flex-wrap items-center justify-between gap-x-6 gap-y-2 lg:mt-6">
        <p className="flex gap-6">
          <CountLink to={`${base}/followers`} n={profile.followerCount} one="follower" />
          <CountLink to={`${base}/following`} n={profile.followingCount} one="following" many="following" />
        </p>
        {mine ? (
          // Rare things (theme, log out) live one level deeper, not where others see Follow.
          <Link to="/settings" className={`${buttonStyles('ghost')} -mr-4`}>
            <GearSix size="1.25em" aria-hidden="true" />
            Settings
          </Link>
        ) : (
          <Follow profile={profile} />
        )}
      </div>
    </header>
  )
}

function CountLink({ to, n, one, many }: { to: string; n: number; one: string; many?: string }) {
  const [number, ...words] = count(n, one, many).split(' ')
  return (
    <Link to={to} className="flex h-tap items-center gap-1 rounded-control text-body">
      <span className="tabular font-medium text-text">{number}</span>
      <span className="text-muted">{words.join(' ')}</span>
    </Link>
  )
}

function Follow({ profile }: { profile: Profile }) {
  const follow = useFollow(profile)
  const toast = useToast()
  return (
    <>
      <FollowButton
        following={profile.followedByMe}
        onToggle={() => {
          const next = !profile.followedByMe
          follow.mutate(next, {
            onError: () => toast.show(`Couldn't ${next ? 'follow' : 'unfollow'} ${nameOf(profile)}. Try again.`),
          })
        }}
      />
      <ToastRegion toast={toast} />
    </>
  )
}

function Ratings({ userId, mine }: { userId: string; mine: boolean }) {
  const ratings = useUserRatings(userId)

  if (ratings.isPending) return <RatingsSkeleton />
  if (ratings.isError && !ratings.data) {
    return <ErrorState message="Couldn't load ratings." onRetry={() => ratings.refetch()} retrying={ratings.isFetching} />
  }

  // A rating whose album catalog doesn't have can't be drawn; leave it out.
  const items = ratings.data.pages.flatMap((p) => p.items).filter((item) => item.album)
  if (!items.length) {
    return mine ? (
      <EmptyState
        message="Nothing rated yet. Find an album you love."
        action={
          <Link to="/search" className={buttonStyles('primary')}>
            Search albums
          </Link>
        }
      />
    ) : (
      <EmptyState message="No ratings yet." />
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <ul className={albumGrid}>
        {items.map((item, index) => (
          <li key={item.id}>
            <AlbumTile
              eager={index < eagerAlbumTiles}
              to={`/albums/${item.albumId}`}
              title={item.album!.title}
              artist={item.album!.artistCredit}
              year={item.album!.year}
              src={item.album!.coverUrl}
              rating={toStars(item.rating)}
            />
          </li>
        ))}
      </ul>
      <LoadMore
        hasMore={ratings.hasNextPage}
        loading={ratings.isFetchingNextPage}
        failed={ratings.isFetchNextPageError}
        onLoad={() => ratings.fetchNextPage()}
        label="Show more ratings"
        error="Couldn't load more ratings."
      />
    </div>
  )
}

function RatingsSkeleton() {
  return (
    <ul aria-busy="true" aria-label="Loading ratings" className={albumGrid}>
      {Array.from({ length: 10 }, (_, i) => (
        <li key={i}>
          <AlbumTileSkeleton />
        </li>
      ))}
    </ul>
  )
}

function ProfileSkeleton() {
  return (
    <main aria-busy="true" aria-label="Loading profile" className={pageFrame}>
      <div className="flex flex-col">
        <Skeleton className="h-12 w-2/3 lg:h-16 lg:w-1/3" />
        <Skeleton className="mt-4 h-4 w-1/3 lg:mt-6 lg:w-1/6" />
        <Skeleton className="mt-6 h-4 w-1/2 lg:mt-8 lg:w-1/4" />
      </div>
      <div className="mt-12 flex flex-col lg:mt-16">
        <Skeleton className="mb-6 h-6 w-1/4 lg:w-1/12" />
        <RatingsSkeleton />
      </div>
    </main>
  )
}

/** /profile: my own profile lives at /u/<my username>. */
export function MyProfileRedirect() {
  const me = useMe()
  if (me.isPending) return <ProfileSkeleton />
  if (me.isError) {
    return (
      <main className={pageFrame}>
        <PageHeader title="Profile" />
        <div className="mt-6 lg:mt-8">
          <ErrorState message="Couldn't load your profile." onRetry={() => me.refetch()} retrying={me.isFetching} />
        </div>
      </main>
    )
  }
  return <Navigate to={`/u/${encodeURIComponent(me.data.username)}`} replace />
}
