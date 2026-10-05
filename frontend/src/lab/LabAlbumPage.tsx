import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { AlbumSkeleton } from '../components/album/AlbumSkeleton.tsx'
import type { DataState } from '../components/album/Reviews.tsx'
import { findAlbum } from './albums.ts'
import { AlbumView } from './AlbumView.tsx'

/** ?state=loading|empty|error forces a state so each can be looked at. */
export function LabAlbumPage() {
  const { mbid } = useParams()
  const [params] = useSearchParams()
  const [retried, setRetried] = useState(false)
  const album = findAlbum(mbid)

  const forced = params.get('state')
  let state: DataState = forced === 'loading' || forced === 'empty' || forced === 'error' ? forced : 'ready'
  if (state === 'error' && retried) state = 'ready'

  if (state === 'loading') return <AlbumSkeleton />

  if (!album) {
    return (
      <main className="mx-auto flex max-w-content flex-col items-start gap-3 px-4 py-16 lg:px-6">
        <p className="text-text">Couldn't find this album.</p>
        <Link to="/lab/grid" className="text-accent-text underline">
          Back to the grid
        </Link>
      </main>
    )
  }

  return <AlbumView key={album.mbid} album={album} state={state} onRetry={() => setRetried(true)} />
}
