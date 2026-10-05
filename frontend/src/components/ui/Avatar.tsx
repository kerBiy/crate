type AvatarProps = {
  name: string
  size?: 'sm' | 'md'
  /** Announce the name. Off by default: avatars usually sit next to the name in text. */
  labelled?: boolean
  className?: string
}

const sizes = { sm: 'size-avatar-sm', md: 'size-avatar' }

export function Avatar({ name, size = 'md', labelled = false, className = '' }: AvatarProps) {
  return (
    <span
      role={labelled ? 'img' : undefined}
      aria-label={labelled ? name : undefined}
      aria-hidden={labelled ? undefined : true}
      className={`inline-flex shrink-0 items-center justify-center rounded-full border border-border bg-surface-raised text-meta font-medium text-text ${sizes[size]} ${className}`}
    >
      {name[0]}
    </span>
  )
}

type AvatarStackProps = { names: string[]; max?: number }

/** Overlapping avatars for a group of people, with "+N" for the rest. */
export function AvatarStack({ names, max = 4 }: AvatarStackProps) {
  const shown = names.slice(0, max)
  const rest = names.length - shown.length

  return (
    <span role="img" aria-label={describe(names, shown.length)} className="inline-flex items-center">
      {shown.map((name, i) => (
        <Avatar key={name} name={name} className={`border-2 border-bg ${i > 0 ? '-ml-2' : ''}`} />
      ))}
      {rest > 0 && <span className="ml-2 text-meta text-muted">+{rest}</span>}
    </span>
  )
}

/** "Ana", "Ana and Radu", "Ana, Radu and 3 others". */
function describe(names: string[], shown: number) {
  if (names.length <= 2) return names.join(' and ')
  const rest = names.length - shown
  if (rest === 0) return `${names.slice(0, -1).join(', ')} and ${names.at(-1)}`
  return `${names.slice(0, shown).join(', ')} and ${rest} other${rest === 1 ? '' : 's'}`
}
