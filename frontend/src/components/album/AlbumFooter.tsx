import { useState } from 'react'
import { useAlbumTracks } from '../../api/queries.ts'
import type { AlbumDetails } from '../../api/types.ts'
import { albumLengthWords, compact, count } from '../../format.ts'
import { Skeleton } from '../ui/Skeleton.tsx'
import { releaseDate } from './text.ts'

/**
 * The quiet facts under the tracklist, Apple Music style, one per line: the release date; songs and
 * length; the label; ListenBrainz's listeners and listens. Songs, length and label come from the
 * release the tracklist is read from (ADR 013): skeleton lines until it has loaded once, left out when
 * unknown. ListenBrainz's line keeps its space while its numbers are fetched, then fades in.
 */
export function AlbumFooter({ album }: { album: AlbumDetails }) {
  const tracks = useAlbumTracks(album.id)
  const fromTracks = tracks.data?.releaseId ? tracks.data : null
  return (
    <AlbumFacts
      album={album}
      release={album.release ?? fromTracks}
      waiting={!album.release && tracks.isPending}
    />
  )
}

type AlbumFactsProps = {
  album: Pick<AlbumDetails, 'id' | 'firstReleaseDate' | 'listenBrainz' | 'listenBrainzRefreshing'>
  release: { trackCount: number | null; lengthMs: number | null; label: string | null } | null
  /** The tracklist (and with it songs, length and label) is still loading. */
  waiting: boolean
}

/** The footer's lines from plain values; AlbumFooter feeds it, /dev/components shows its states. */
export function AlbumFacts({ album, release, waiting }: AlbumFactsProps) {
  const date = releaseDate(album.firstReleaseDate)
  const songs = release?.trackCount
    ? [count(release.trackCount, 'song'), release.lengthMs ? albumLengthWords(release.lengthMs) : null].filter(Boolean).join(', ')
    : null

  return (
    <div className="flex flex-col gap-1 text-meta text-muted">
      {date && <p>{date}</p>}
      {waiting ? (
        <>
          <Line width="w-1/2" />
          <Line width="w-1/3" />
        </>
      ) : (
        <>
          {songs && <p className="tabular">{songs}</p>}
          {release?.label && <p>{release.label}</p>}
        </>
      )}
      <ListenBrainzLine album={album} />
    </div>
  )
}

/** A skeleton the height of one line of meta text. */
function Line({ width }: { width: string }) {
  return (
    <p aria-hidden="true">
      <Skeleton shape="text" className={width} />
    </p>
  )
}

/** ListenBrainz's numbers, never Crate's: they say whose they are, and link there. */
function ListenBrainzLine({ album }: { album: AlbumFactsProps['album'] }) {
  // Fade only numbers that arrive after the page did; numbers already there just show.
  const [arrivedLate] = useState(album.listenBrainz === null)
  const numbers = album.listenBrainz
  if (!numbers && !album.listenBrainzRefreshing) return null

  const full = new Intl.NumberFormat('en')
  return (
    // While the numbers are fetched: the same line, invisible (also to keyboard and screen readers),
    // holding its space.
    <p className={numbers ? (arrivedLate ? 'fade-in' : '') : 'invisible'}>
      <span className="tabular" aria-hidden="true">
        {numbers ? `${compact(numbers.listeners)} listeners and ${compact(numbers.listens)} listens` : '00.0k listeners and 0.0m listens'}
      </span>
      {numbers && (
        <span className="sr-only">
          {full.format(numbers.listeners)} listeners and {full.format(numbers.listens)} listens
        </span>
      )}{' '}
      on{' '}
      <a
        href={`https://listenbrainz.org/album/${encodeURIComponent(album.id)}/`}
        target="_blank"
        rel="noreferrer"
        className="font-medium text-accent-text underline"
      >
        ListenBrainz
      </a>
    </p>
  )
}
