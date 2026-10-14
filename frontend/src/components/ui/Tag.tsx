/**
 * A fact that isn't a control, such as a genre. Deliberately not pressable: no hover, no pressed state,
 * no focus. When tags lead somewhere, make them links with the pressed state and a tap-sized target.
 */
export function Tag({ children }: { children: string }) {
  return (
    <span className="inline-block rounded-full border border-border bg-surface px-3 py-1 text-meta text-muted">
      {children}
    </span>
  )
}

/** Tags in a wrapping row, as a list so screen readers announce how many. */
export function TagList({ tags, label }: { tags: string[]; label: string }) {
  return (
    <ul aria-label={label} className="flex flex-wrap gap-2">
      {tags.map((tag) => (
        <li key={tag}>
          <Tag>{tag}</Tag>
        </li>
      ))}
    </ul>
  )
}

/** Tags still loading: pills of the same size, pulsing in place. */
export function TagListSkeleton({ widths }: { widths: string[] }) {
  return (
    <div aria-hidden="true" className="flex flex-wrap gap-2">
      {widths.map((text, i) => (
        // An invisible word sizes each pill exactly like a real tag.
        <span key={i} className="skeleton inline-block rounded-full border border-transparent px-3 py-1 text-meta">
          <span className="invisible">{text}</span>
        </span>
      ))}
    </div>
  )
}
