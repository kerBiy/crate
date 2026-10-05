import { useState } from 'react'

type CoverProps = {
  /** Image URL. Missing or broken: a neutral sleeve with the title's initials. */
  src?: string
  title: string
  artist: string
}

export function Cover({ src, title, artist }: CoverProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const alt = `Cover of ${title} by ${artist}`

  if (!src || failedSrc === src) {
    return (
      <div
        role="img"
        aria-label={alt}
        className="flex aspect-square w-full items-center justify-center rounded-cover border border-border bg-surface font-display text-h2 text-muted"
      >
        {initials(title)}
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={alt}
      onError={() => setFailedSrc(src)}
      className="block aspect-square w-full rounded-cover bg-surface object-cover"
    />
  )
}

function initials(title: string) {
  return title
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase())
    .join('')
}
