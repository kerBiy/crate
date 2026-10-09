import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useAlbum } from '../api/queries.ts'
import { AlbumSkeleton } from '../components/album/AlbumSkeleton.tsx'
import { AlbumView } from '../components/album/AlbumView.tsx'
import { buttonStyles } from '../components/ui/Button.tsx'
import { pageFrame } from '../components/ui/PageHeader.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'

export function AlbumPage() {
  const { id = '' } = useParams()
  const album = useAlbum(id)

  if (album.isPending) return <AlbumSkeleton />

  if (album.isError) {
    // 404: MusicBrainz doesn't know it. 400: not even a valid id. Either way, nothing to retry.
    const missing = album.error instanceof ApiError && (album.error.status === 404 || album.error.status === 400)
    return (
      <main className={pageFrame}>
        {missing ? (
          <EmptyState
            message="Couldn't find this album."
            action={
              <Link to="/search" className={buttonStyles('primary')}>
                Search albums
              </Link>
            }
          />
        ) : (
          <ErrorState message="Couldn't load this album." onRetry={() => album.refetch()} retrying={album.isFetching} />
        )}
      </main>
    )
  }

  return <AlbumView key={album.data.id} album={album.data} />
}
