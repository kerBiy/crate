import type { ComponentProps, ReactNode } from 'react'
import type { Friend } from '../../lab/albums.ts'
import { Stars } from './Stars.tsx'

type ButtonProps = ComponentProps<'button'> & {
  kind?: 'primary' | 'secondary'
  icon?: ReactNode
}

export function Button({ kind = 'secondary', icon, children, className = '', ...rest }: ButtonProps) {
  const look =
    kind === 'primary'
      ? 'bg-accent text-on-accent'
      : 'border border-border bg-surface text-text'
  return (
    <button
      type="button"
      className={`inline-flex h-tap items-center justify-center gap-2 rounded-control px-4 text-body font-medium ${look} ${className}`}
      {...rest}
    >
      {icon}
      {children}
    </button>
  )
}

export function Avatar({ name, small = false }: { name: string; small?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center rounded-full border border-border bg-surface-raised font-medium text-text ${
        small ? 'size-avatar-sm text-meta' : 'size-avatar text-meta'
      }`}
    >
      {name[0]}
    </span>
  )
}

export function FriendRatings({ friends }: { friends: Friend[] }) {
  if (friends.length === 0) {
    return <p className="text-muted">None of your friends have rated this yet.</p>
  }
  return (
    <ul className="flex flex-col gap-2">
      {friends.map((friend) => (
        <li key={friend.name} className="flex items-center gap-2">
          <Avatar name={friend.name} small />
          <span className="flex flex-col">
            <span className="text-meta text-text">{friend.name}</span>
            <Stars value={friend.rating} className="text-meta" />
          </span>
        </li>
      ))}
    </ul>
  )
}
