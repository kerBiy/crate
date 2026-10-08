import { Link } from 'react-router'
import { Avatar } from './Avatar.tsx'
import { Skeleton } from './Skeleton.tsx'

type PersonRowProps = { username: string; displayName: string | null }

/** Someone in a list of people (search, followers): avatar and name, linking to their profile. */
export function PersonRow({ username, displayName }: PersonRowProps) {
  const name = displayName || username
  return (
    <Link to={`/u/${encodeURIComponent(username)}`} className="flex min-h-tap items-center gap-3 rounded-control py-2">
      <Avatar name={name.toUpperCase()} />
      <span className="flex min-w-0 flex-col">
        <span className="truncate font-medium text-text">{name}</span>
        {displayName && <span className="truncate text-meta text-muted">{username}</span>}
      </span>
    </Link>
  )
}

export function PersonRowSkeleton() {
  return (
    <div aria-hidden="true" className="flex min-h-tap items-center gap-3 py-2">
      <Skeleton shape="circle" className="size-avatar" />
      <Skeleton className="h-3 w-1/3 lg:w-1/6" />
    </div>
  )
}
