import { useState } from 'react'
import { coverUrl, type Album } from '../../lab/albums.ts'

type CoverProps = { album: Album; size: 250 | 500 }

export function Cover({ album, size }: CoverProps) {
  const [failed, setFailed] = useState(false)
  const alt = `Cover of ${album.title} by ${album.artist}`

  if (failed) {
    return (
      <div
        role="img"
        aria-label={alt}
        className="flex aspect-square w-full items-center justify-center rounded-cover border border-border bg-surface font-display text-h2 text-muted"
      >
        {initials(album.title)}
      </div>
    )
  }

  return (
    <img
      src={coverUrl(album.mbid, size)}
      alt={alt}
      onError={() => setFailed(true)}
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
