/**
 * A fact that isn't a control, such as a genre, tinted with the accent as the album's identity (DESIGN.md
 * section 4.2). Deliberately not pressable: no hover, no pressed state, no focus. The transparent border
 * keeps it the size of its skeleton. When tags lead somewhere, make them links with the pressed state and
 * a tap-sized target.
 */
export function Tag({ children }: { children: string }) {
  return (
    <span className="inline-block rounded-full border border-transparent bg-accent-soft px-3 py-1 text-meta text-accent-text">
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
