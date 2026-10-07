import { AlbumTile } from '../components/ui/AlbumTile.tsx'
import { albums, coverUrl } from './albums.ts'

// The five albums repeated, enough to fill three desktop rows.
const tiles = Array.from({ length: 18 }, (_, i) => albums[i % albums.length])

/** Cover grid: the main home of the vinyl signature (record slides out on hover). */
export function LabGridPage() {
  return (
    <main className="mx-auto max-w-content px-4 py-8 lg:px-6 lg:py-12">
      <h1 className="mb-6 font-display text-h3 font-semibold text-text">Recently rated</h1>
      <ul className="grid grid-cols-3 gap-3 md:grid-cols-4 lg:grid-cols-6">
        {tiles.map((album, i) => (
          <li key={i}>
            <AlbumTile
              eager={i < 6}
              to={`/lab/album/${album.mbid}`}
              title={album.title}
              artist={album.artist}
              src={coverUrl(album.mbid, 250)}
              color={album.dominantColor}
            />
          </li>
        ))}
      </ul>
    </main>
  )
}
