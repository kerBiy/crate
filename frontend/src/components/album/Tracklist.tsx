import { useId, useRef, useState } from 'react'
import { useAlbumTracks } from '../../api/queries.ts'
import type { AlbumTracks, Track } from '../../api/types.ts'
import { trackLength } from '../../format.ts'
import { Button } from '../ui/Button.tsx'
import { Skeleton } from '../ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../ui/States.tsx'
import type { DataState } from './Reviews.tsx'

/** Shown before "Show all"; albums with up to {@link showAllUpTo} tracks show them all, no button. */
const collapsedCount = 5
const showAllUpTo = 7

/**
 * The album's tracklist from GET /api/albums/{id}/tracks, loaded on its own after the page is up.
 * {@code trackCount}, when the album already knows it, shapes the skeleton like the real list.
 */
export function AlbumTracklist({ albumId, trackCount }: { albumId: string; trackCount?: number }) {
  const tracks = useAlbumTracks(albumId)
  if (tracks.isPending) return <Tracklist discs={[]} state="loading" onRetry={() => {}} expectedCount={trackCount} />
  if (tracks.isError) {
    return <Tracklist discs={[]} state="error" onRetry={() => tracks.refetch()} retrying={tracks.isFetching} />
  }
  const { discs } = tracks.data
  return <Tracklist discs={discs} state={discs.length ? 'ready' : 'empty'} onRetry={() => tracks.refetch()} />
}

type TracklistProps = {
  discs: AlbumTracks['discs']
  state: DataState
  onRetry: () => void
  retrying?: boolean
  /** While loading: how many tracks to expect, if known. */
  expectedCount?: number
}

/**
 * Rows like Apple Music's album view: the number in its own column, the title, the length at the end,
 * and the hairline starting at the title. The first five, then "Show all N tracks", counted across
 * discs; disc headings only when there is more than one disc.
 */
export function Tracklist({ discs, state, onRetry, retrying = false, expectedCount }: TracklistProps) {
  const [expanded, setExpanded] = useState(false)
  const listId = useId()
  const listRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  /**
   * Nothing jumps when the list changes length, even when that pins or unpins the album page's left
   * column (DESIGN.md section 8): Show all keeps the list where it is (the new rows grow downward from
   * the fifth track); Show fewer keeps the button under the finger while the rows above it go.
   */
  function toggle() {
    const anchor = expanded ? buttonRef.current : listRef.current
    if (anchor) keepOnScreen(anchor)
    setExpanded(!expanded)
  }

  if (state === 'loading') return <TracklistSkeleton expectedCount={expectedCount} />
  if (state === 'error') return <ErrorState message="Couldn't load the tracklist." onRetry={onRetry} retrying={retrying} />
  if (state === 'empty' || discs.length === 0) return <EmptyState message="No tracklist on MusicBrainz yet." />

  const total = discs.reduce((sum, disc) => sum + disc.tracks.length, 0)
  const collapsible = total > showAllUpTo
  const shown = collapsible && !expanded ? firstTracks(discs, collapsedCount) : discs

  return (
    <div className="flex flex-col gap-6">
      <div id={listId} ref={listRef} className="flex flex-col gap-8">
        {discs.length === 1 ? (
          <TrackRows tracks={shown[0].tracks} />
        ) : (
          shown.map((disc) => (
            <section key={disc.position} aria-labelledby={`${listId}-disc-${disc.position}`}>
              <h3 id={`${listId}-disc-${disc.position}`} className="mb-2 text-body font-medium text-muted">
                Disc {disc.position}
              </h3>
              <TrackRows tracks={disc.tracks} />
            </section>
          ))
        )}
      </div>
      {collapsible && (
        <div>
          <Button ref={buttonRef} aria-expanded={expanded} aria-controls={listId} onClick={toggle}>
            {expanded ? 'Show fewer' : `Show all ${total} tracks`}
          </Button>
        </div>
      )}
    </div>
  )
}

/**
 * Holds the element at its current place on the screen while the page settles: the list re-renders,
 * then the left column may switch between pinned and scrolling a frame later (its height is measured
 * by a ResizeObserver), so it corrects over a few frames.
 */
function keepOnScreen(element: HTMLElement) {
  const top = element.getBoundingClientRect().top
  let frames = 0
  const settle = () => {
    const moved = element.getBoundingClientRect().top - top
    if (moved !== 0) window.scrollBy(0, moved)
    if (++frames < 4) requestAnimationFrame(settle)
  }
  requestAnimationFrame(settle)
}

/** The first {@code count} tracks across discs, keeping only the discs they're on. */
function firstTracks(discs: AlbumTracks['discs'], count: number) {
  let left = count
  return discs
    .map((disc) => {
      const tracks = disc.tracks.slice(0, Math.max(0, left))
      left -= tracks.length
      return { ...disc, tracks }
    })
    .filter((disc) => disc.tracks.length > 0)
}

function TrackRows({ tracks }: { tracks: Track[] }) {
  return (
    <ol className="rows flex flex-col">
      {tracks.map((track) => (
        <li key={track.position} className="flex items-baseline gap-4">
          {/* The position, 1 to n per disc, like Apple Music. Not the sleeve's printed number: vinyl prints
              sides ("A1", "ONE 1"), which reads as a label and breaks the column. */}
          <span className="tabular min-w-6 shrink-0 text-right text-body text-muted">{track.position}</span>
          <span className="row-rule flex min-w-0 flex-1 items-baseline justify-between gap-4 py-3">
            <span className="min-w-0 truncate text-body text-text">{track.title}</span>
            {track.lengthMs !== null && (
              <span className="tabular shrink-0 text-body text-muted">{trackLength(track.lengthMs)}</span>
            )}
          </span>
        </li>
      ))}
    </ol>
  )
}

/**
 * Same rows, same rules, in the collapsed shape: as many rows as will show, and the button's place when
 * there will be one. Without a known count, the usual case: five rows and a button.
 */
function TracklistSkeleton({ expectedCount }: { expectedCount?: number }) {
  const collapsible = expectedCount === undefined || expectedCount > showAllUpTo
  const rows = collapsible ? collapsedCount : expectedCount
  const titles = ['w-1/2', 'w-2/3', 'w-1/3', 'w-1/2', 'w-2/3', 'w-1/4', 'w-1/2']
  return (
    <div aria-busy="true" aria-label="Loading tracks" className="flex flex-col gap-6">
      <ol className="rows flex flex-col">
        {titles.slice(0, rows).map((width, i) => (
          <li key={i} className="flex items-baseline gap-4">
            <span className="min-w-6 shrink-0 text-right text-body">
              <Skeleton shape="text" className="w-3" />
            </span>
            {/* The bars sit in text spans, so each row gets a real line's height, like the rows they stand for. */}
            <span className="row-rule flex min-w-0 flex-1 items-baseline justify-between gap-4 py-3">
              <span className="min-w-0 flex-1 text-body">
                <Skeleton shape="text" className={width} />
              </span>
              <span className="shrink-0 text-body">
                <Skeleton shape="text" className="w-8" />
              </span>
            </span>
          </li>
        ))}
      </ol>
      {collapsible && <Skeleton shape="block" className="h-tap w-1/3" />}
    </div>
  )
}
