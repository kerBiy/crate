import { Avatar } from '../ui/Avatar.tsx'
import { RatingStars } from '../ui/RatingStars.tsx'

/** A friend's rating of this album. */
export type Friend = { name: string; rating: number }

export function FriendRatings({ friends }: { friends: Friend[] }) {
  if (friends.length === 0) {
    return <p className="text-muted">None of your friends have rated this yet.</p>
  }
  return (
    <ul className="flex flex-col gap-2">
      {friends.map((friend) => (
        <li key={friend.name} className="flex items-center gap-2">
          <Avatar name={friend.name} size="sm" />
          <span className="flex flex-col">
            <span className="text-meta text-text">{friend.name}</span>
            <RatingStars value={friend.rating} className="text-meta" />
          </span>
        </li>
      ))}
    </ul>
  )
}
