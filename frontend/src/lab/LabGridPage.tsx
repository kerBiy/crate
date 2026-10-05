import { Link } from 'react-router'
import { Cover } from '../components/album/Cover.tsx'
import { Vinyl } from '../components/album/Vinyl.tsx'
import { albums } from './albums.ts'

// The five albums repeated, enough to fill three desktop rows.
const tiles = Array.from({ length: 18 }, (_, i) => albums[i % albums.length])

/** Cover grid: the main home of the vinyl signature (record slides out on hover). */
export function LabGridPage() {
  return (
    <main className="mx-auto max-w-content px-4 py-8 lg:px-6 lg:py-12">
      <h1 className="mb-6 font-display text-h3 font-semibold text-text">Recently rated</h1>
      <ul className="grid grid-cols-3 gap-3 md:grid-cols-4 lg:grid-cols-6">
        {tiles.map((album, i) => (
          <li key={i} className="tile">
            <Link to={`/lab/album/${album.mbid}`} className="block rounded-cover">
              <div className="tile-art">
                <div className="tile-record" aria-hidden="true">
                  <Vinyl color={album.dominantColor} />
                </div>
                <div className="tile-cover">
                  <Cover album={album} size={250} />
                </div>
              </div>
              <p className="mt-1 line-clamp-2 text-meta text-muted">
                <span className="font-narrow font-display font-semibold">{album.title}</span>
                <br />
                {album.artist}
              </p>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  )
}
