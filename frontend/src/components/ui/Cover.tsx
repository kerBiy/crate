import { useEffect, useEffectEvent, useState } from 'react'

type CoverProps = {
  /** Image URL. Missing or broken: a neutral sleeve with the title's initials. */
  src?: string
  title: string
  artist: string
  /** Visible on arrival (first grid row, album page): load now, at high priority. Otherwise lazy. */
  eager?: boolean
  /** Called once the cover is settled: the image has loaded, or the initials replaced it. */
  onSettled?: () => void
  /** sm: thumbnails in rows (feed), so the initials of a missing cover fit. md: grids. lg: the album page. */
  size?: 'sm' | 'md' | 'lg'
}

const radius = { sm: 'rounded-cover', md: 'rounded-cover', lg: 'rounded-cover-lg' }
const initialsSize = { sm: 'text-body', md: 'text-h2', lg: 'text-h1' }

type Status = 'loading' | 'loaded' | 'missing'

/**
 * Album art in a square that never changes size. Until the image arrives the square pulses;
 * then the image fades in, with its own hairline edge. Each cover does this on its own.
 */
export function Cover({ src, title, artist, eager = false, onSettled, size = 'md' }: CoverProps) {
  // Keyed by URL, so a new src starts loading again instead of inheriting the old state.
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const status: Status = !src || failedSrc === src ? 'missing' : loadedSrc === src ? 'loaded' : 'loading'
  const alt = `Cover of ${title} by ${artist}`

  const settled = useEffectEvent(() => onSettled?.())
  useEffect(() => {
    if (status !== 'loading') settled()
  }, [status])

  if (status === 'missing' || !src) {
    return (
      <div
        role="img"
        aria-label={alt}
        className={`flex aspect-square w-full items-center justify-center border border-border bg-surface font-display text-muted ${radius[size]} ${initialsSize[size]}`}
      >
        {initials(title)}
      </div>
    )
  }

  const loaded = status === 'loaded'
  return (
    <div className={`relative aspect-square w-full overflow-hidden ${radius[size]} ${loaded ? 'cover-edge bg-surface' : 'skeleton'}`}>
      <img
        // An image already in the browser cache can finish before React listens: check on mount.
        ref={(img) => {
          if (img?.complete && img.naturalWidth > 0) setLoadedSrc(src)
        }}
        src={src}
        alt={alt}
        loading={eager ? 'eager' : 'lazy'}
        fetchPriority={eager ? 'high' : 'auto'}
        decoding="async"
        onLoad={() => setLoadedSrc(src)}
        onError={() => setFailedSrc(src)}
        data-loaded={loaded ? '' : undefined}
        className="cover-img absolute inset-0 size-full object-cover"
      />
    </div>
  )
}

function initials(title: string) {
  return title
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase())
    .join('')
}
