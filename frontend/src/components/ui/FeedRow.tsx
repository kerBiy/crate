import { Link } from 'react-router'
import { Avatar } from './Avatar.tsx'
import { Cover } from './Cover.tsx'
import { RatingStars } from './RatingStars.tsx'
import { Skeleton } from './Skeleton.tsx'

type FeedRowProps = {
  /** Who rated: shown name and profile username (null when the account is gone). */
  name: string
  username: string | null
  albumId: string
  title: string
  artist: string
  coverUrl?: string
  /** Stars, 0.5–5. */
  rating: number
  body: string | null
  /** ISO timestamp, and how to show it ("3h"). */
  at: string
  when: string
}

/**
 * One activity in the feed: "ana rated OK Computer", stars, a short review, when. Small cover, no record.
 * Two targets, never nested: the avatar opens the person (a 44px target), and the rest of the row
 * opens the album (the title's link stretched over the row, see .row-link). Tab goes avatar, then row.
 * The name is a link too, for the pointer only; the avatar is its keyboard and touch target.
 * Avatars and text line up with PersonRow. In a `rows` list the separator starts at the text, after the avatar.
 */
export function FeedRow({ name, username, albumId, title, artist, coverUrl, rating, body, at, when }: FeedRowProps) {
  const profile = username ? `/u/${encodeURIComponent(username)}` : null
  return (
    <article className="row-press relative flex pt-4">
      {profile ? (
        // The avatar's own circle, its tap target grown to 44px around it (.tap-area).
        <Link to={profile} aria-label={name} className="tap-area z-10 mt-2 flex shrink-0 self-start rounded-full">
          <Avatar name={name.toUpperCase()} />
        </Link>
      ) : (
        <Avatar name={name.toUpperCase()} className="mt-2 self-start" />
      )}
      <div className="row-rule ml-3 flex min-w-0 flex-1 gap-3 pb-4">
        <div className="flex min-w-0 flex-1 flex-col gap-1 pt-3">
          <p className="text-text">
            {profile ? (
              <>
                {/* Desktop: the name links to the person. Touch: a tap on it opens the album, like the rest of the row. */}
                <Link to={profile} tabIndex={-1} className="relative z-10 hidden font-medium lg:inline">
                  {name}
                </Link>
                <span className="font-medium lg:hidden">{name}</span>
              </>
            ) : (
              <span className="font-medium">{name}</span>
            )}{' '}
            <span className="text-muted">rated</span>{' '}
            <Link to={`/albums/${albumId}`} className="row-link font-display font-semibold">
              {title}
            </Link>
          </p>
          <p className="flex flex-wrap items-center gap-x-3 text-meta">
            <RatingStars value={rating} />
            <time dateTime={at} className="text-muted">
              {when}
            </time>
          </p>
          {body && <p className="mt-1 line-clamp-2 max-w-prose text-lead text-text">{body}</p>}
        </div>
        <div className="w-thumb shrink-0 self-start pt-1">
          <Cover src={coverUrl} title={title} artist={artist} size="sm" />
        </div>
      </div>
    </article>
  )
}

export function FeedRowSkeleton() {
  return (
    <div aria-hidden="true" className="flex pt-4">
      <Skeleton shape="circle" className="mt-2 size-avatar" />
      <div className="row-rule ml-3 flex min-w-0 flex-1 gap-3 pb-4">
        <div className="flex w-full flex-col gap-2 pt-4">
          <Skeleton className="h-3 w-2/3" />
          <Skeleton className="h-3 w-1/4" />
          <Skeleton className="mt-2 h-3 w-full" />
        </div>
        <div className="pt-1">
          <Skeleton shape="block" className="size-thumb" />
        </div>
      </div>
    </div>
  )
}
