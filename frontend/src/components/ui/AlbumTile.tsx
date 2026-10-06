import { Link } from 'react-router'
import { Cover } from './Cover.tsx'
import { Vinyl } from './Vinyl.tsx'

type AlbumTileProps = {
  to: string
  title: string
  artist: string
  src?: string
  /** Album's dominant color, tints the record's label. */
  color?: string
}

/** Grid cover: on hover or keyboard focus the sleeve slides left and the record emerges (pointer devices). */
export function AlbumTile({ to, title, artist, src, color }: AlbumTileProps) {
  return (
    <div className="tile">
      <Link to={to} className="block rounded-cover">
        <div className="tile-art">
          <div className="tile-record" aria-hidden="true">
            <Vinyl color={color} />
          </div>
          <div className="tile-cover">
            <Cover src={src} title={title} artist={artist} />
          </div>
        </div>
        <p className="mt-1 line-clamp-2 text-meta text-muted">
          <span className="font-narrow font-display font-semibold">{title}</span>
          <br />
          {artist}
        </p>
      </Link>
    </div>
  )
}
