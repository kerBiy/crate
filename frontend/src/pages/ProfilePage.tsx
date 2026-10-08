import { Link, Navigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { toStars, useFollow, useMe, useProfile, useUserRatings } from '../api/queries.ts'
import { logout } from '../api/queryClient.ts'
import type { Profile } from '../api/types.ts'
import { SectionTitle } from '../components/album/Reviews.tsx'
import { LoadMore } from '../components/LoadMore.tsx'
import { AlbumTile, AlbumTileSkeleton } from '../components/ui/AlbumTile.tsx'
import { Button, buttonStyles } from '../components/ui/Button.tsx'
import { FollowButton } from '../components/ui/FollowButton.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { ToastRegion, useToast } from '../components/ui/Toast.tsx'
import { count, nameOf } from '../format.ts'

const page = 'mx-auto flex max-w-content flex-col gap-12 px-4 py-8 lg:px-6 lg:py-12'
const grid = 'grid grid-cols-3 gap-3 md:grid-cols-4 lg:grid-cols-6'
const eagerTiles = 6

/** /u/:username: who they are, follow counts, Follow, and the albums they rated. */
export function ProfilePage() {
  const { username = '' } = useParams()
  const profile = useProfile(username)
  const me = useMe()

  if (profile.isPending) return <ProfileSkeleton />
  if (profile.isError) {
    const missing = profile.error instanceof ApiError && profile.error.status === 404
    return (
      <main className={page}>
        <h1 className="font-display text-h3 font-semibold text-text">{missing ? 'Not found' : 'Profile'}</h1>
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
      </main>
    )
  }

  const person = profile.data
  const mine = me.data?.id === person.id
  return (
    <main className={page}>
      <ProfileHeader profile={person} mine={mine} />
      <section className="flex flex-col">
        <SectionTitle>Rated</SectionTitle>
        <Ratings userId={person.id} mine={mine} />
      </section>
    </main>
  )
}

function ProfileHeader({ profile, mine }: { profile: Profile; mine: boolean }) {
  const base = `/u/${encodeURIComponent(profile.username)}`
  return (
    <header className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-col">
          <h1 className="break-words font-display text-h2 font-semibold text-text lg:text-h1">{nameOf(profile)}</h1>
          {profile.displayName && <p className="text-lead text-muted">{profile.username}</p>}
        </div>
        <p className="flex gap-6">
          <CountLink to={`${base}/followers`} n={profile.followerCount} one="follower" />
          <CountLink to={`${base}/following`} n={profile.followingCount} one="following" many="following" />
        </p>
      </div>
      {mine ? (
        <Button variant="ghost" onClick={logout} className="-mx-4 self-start lg:self-auto">
          Log out
        </Button>
      ) : (
        <Follow profile={profile} />
      )}
    </header>
  )
}

function CountLink({ to, n, one, many }: { to: string; n: number; one: string; many?: string }) {
  const [number, ...words] = count(n, one, many).split(' ')
  return (
    <Link to={to} className="flex h-tap items-center gap-1 rounded-control text-body">
      <span className="font-medium text-text">{number}</span>
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
        className="self-start lg:self-auto"
        onToggle={() => {
          const next = !profile.followedByMe
          follow.mutate(next, {
            onError: () => toast.show(`Couldn't ${next ? 'follow' : 'unfollow'} ${nameOf(profile)}. Try again.`),
          })
        }}
      />
      <ToastRegion message={toast.message} />
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
      <ul className={grid}>
        {items.map((item, index) => (
          <li key={item.id}>
            <AlbumTile
              eager={index < eagerTiles}
              to={`/albums/${item.albumId}`}
              title={item.album!.title}
              artist={item.album!.artistCredit}
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
    <ul aria-busy="true" aria-label="Loading ratings" className={grid}>
      {Array.from({ length: 6 }, (_, i) => (
        <li key={i}>
          <AlbumTileSkeleton />
        </li>
      ))}
    </ul>
  )
}

function ProfileSkeleton() {
  return (
    <main aria-busy="true" aria-label="Loading profile" className={page}>
      <div className="flex flex-col gap-4">
        <Skeleton className="h-12 w-2/3 lg:w-1/3" />
        <Skeleton className="h-4 w-1/2 lg:w-1/4" />
      </div>
      <RatingsSkeleton />
    </main>
  )
}

/** /profile: my own profile lives at /u/<my username>. */
export function MyProfileRedirect() {
  const me = useMe()
  if (me.isPending) return <ProfileSkeleton />
  if (me.isError) {
    return (
      <main className={page}>
        <ErrorState message="Couldn't load your profile." onRetry={() => me.refetch()} retrying={me.isFetching} />
      </main>
    )
  }
  return <Navigate to={`/u/${encodeURIComponent(me.data.username)}`} replace />
}
