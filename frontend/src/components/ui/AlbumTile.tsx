import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Cover } from './Cover.tsx'
import { RatingStars } from './RatingStars.tsx'
import { Skeleton } from './Skeleton.tsx'
import { Vinyl } from './Vinyl.tsx'

type AlbumTileProps = {
  to: string
  title: string
  artist: string
  src?: string
  /** Album's dominant color, tints the record's label. */
  color?: string
  /** In the first visible row: load the cover right away instead of lazily. */
  eager?: boolean
  /** Release year, after the artist: tells reissues and namesakes apart. */
  year?: string | null
  /** Someone's rating of it, in stars (profile grid). Shown under the caption. */
  rating?: number
}

/**
 * Grid cover: on hover or keyboard focus the sleeve slides left and the record emerges (pointer
 * devices), but only once the cover is in: no record sliding out from behind a placeholder.
 */
export function AlbumTile({ to, title, artist, year, src, color, eager, rating }: AlbumTileProps) {
  const [ready, setReady] = useState(false)

  return (
    <div className="tile" data-ready={ready ? '' : undefined}>
      <Link to={to} className="block rounded-cover">
        <div className="tile-art">
          <div className="tile-record" aria-hidden="true">
            <Vinyl color={color} />
          </div>
          <div className="tile-cover">
            <Cover src={src} title={title} artist={artist} eager={eager} onSettled={() => setReady(true)} />
          </div>
        </div>
        <TileCaption>
          <span className="block truncate text-body font-medium text-text">{title}</span>
          <span className="block truncate text-meta text-muted">{year ? `${artist}, ${year}` : artist}</span>
        </TileCaption>
        {rating !== undefined && <RatingStars value={rating} className="mt-1 text-meta" />}
      </Link>
    </div>
  )
}

/** The whole grid while results load: same square and the same two caption lines, so nothing moves. */
export function AlbumTileSkeleton() {
  return (
    <div aria-hidden="true">
      <Skeleton shape="block" className="aspect-square w-full" />
      <TileCaption>
        <span className="block text-body">
          <Skeleton shape="text" className="w-3/4" />
        </span>
        <span className="block text-meta">
          <Skeleton shape="text" className="w-1/2" />
        </span>
      </TileCaption>
    </div>
  )
}

function TileCaption({ children }: { children: ReactNode }) {
  return <p className="mt-3 flex flex-col">{children}</p>
}
