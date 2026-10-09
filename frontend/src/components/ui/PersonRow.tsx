import { Link } from 'react-router'
import { Avatar } from './Avatar.tsx'
import { Skeleton } from './Skeleton.tsx'

type PersonRowProps = { username: string; displayName: string | null }

/** Someone in a list of people (search, followers): avatar and name, linking to their profile. In a `rows` list the separator starts at the name. */
export function PersonRow({ username, displayName }: PersonRowProps) {
  const name = displayName || username
  return (
    <Link to={`/u/${encodeURIComponent(username)}`} className="flex items-center gap-3 rounded-control">
      <Avatar name={name.toUpperCase()} />
      <span className="row-rule flex min-h-tap min-w-0 flex-1 flex-col justify-center py-3">
        <span className="truncate font-medium text-text">{name}</span>
        {displayName && <span className="truncate text-meta text-muted">{username}</span>}
      </span>
    </Link>
  )
}

export function PersonRowSkeleton() {
  return (
    <div aria-hidden="true" className="flex items-center gap-3">
      <Skeleton shape="circle" className="size-avatar" />
      <span className="row-rule flex min-h-tap flex-1 items-center py-3">
        <Skeleton className="h-3 w-1/3 lg:w-1/6" />
      </span>
    </div>
  )
}
