import { Link } from 'react-router'
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

/** One activity in the feed: "ana rated OK Computer", stars, a short review, when. Small cover, no record. */
export function FeedRow({ name, username, albumId, title, artist, coverUrl, rating, body, at, when }: FeedRowProps) {
  const album = `/albums/${albumId}`
  return (
    <article className="flex gap-3 py-4">
      <Link to={album} className="w-thumb shrink-0 self-start rounded-cover" tabIndex={-1} aria-hidden="true">
        <Cover src={coverUrl} title={title} artist={artist} size="sm" />
      </Link>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-text">
          {username ? (
            <Link to={`/u/${encodeURIComponent(username)}`} className="font-medium">
              {name}
            </Link>
          ) : (
            <span className="font-medium">{name}</span>
          )}{' '}
          <span className="text-muted">rated</span>{' '}
          <Link to={album} className="font-display font-semibold">
            {title}
          </Link>
        </p>
        <p className="flex flex-wrap items-center gap-x-3 text-meta">
          <RatingStars value={rating} />
          <time dateTime={at} className="text-muted">
            {when}
          </time>
        </p>
        {body && <p className="line-clamp-2 max-w-prose text-text">{body}</p>}
      </div>
    </article>
  )
}

export function FeedRowSkeleton() {
  return (
    <div aria-hidden="true" className="flex gap-3 py-4">
      <Skeleton shape="block" className="size-thumb" />
      <div className="flex w-full max-w-prose flex-col gap-2 pt-1">
        <Skeleton className="h-3 w-2/3" />
        <Skeleton className="h-3 w-1/4" />
        <Skeleton className="h-3 w-full" />
      </div>
    </div>
  )
}
