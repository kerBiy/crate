import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { minSearchLength, useAlbumSearch, usePeopleSearch } from '../api/queries.ts'
import { AlbumTile, AlbumTileSkeleton } from '../components/ui/AlbumTile.tsx'
import { Input } from '../components/ui/Input.tsx'
import { PersonRow } from '../components/ui/PersonRow.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { PeopleSkeleton } from './FollowListPage.tsx'

type Kind = 'albums' | 'people'
const kinds: { id: Kind; label: string }[] = [
  { id: 'albums', label: 'Albums' },
  { id: 'people', label: 'People' },
]
const fields: Record<Kind, { label: string; hint: string }> = {
  albums: { label: 'Search albums', hint: 'Album or artist' },
  people: { label: 'Search people', hint: 'Name or username' },
}

const grid = 'grid grid-cols-3 gap-3 md:grid-cols-4 lg:grid-cols-6'
// The first row at the widest grid (two rows on mobile) is on screen at once: load those covers eagerly.
const eagerTiles = 6

/** Waits until the value has stopped changing for `delay` ms. */
function useDebounced<T>(value: T, delay: number) {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return settled
}

/**
 * One big field for albums or people. Albums show as a cover grid, people as a list. The query and
 * the kind live in the URL (?q=, ?type=people), so Back restores them.
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const kind: Kind = params.get('type') === 'people' ? 'people' : 'albums'
  const [text, setText] = useState(params.get('q') ?? '')
  // Long enough that a pause mid-word doesn't fire a search: every new album query may reach MusicBrainz.
  const query = useDebounced(text.trim(), 350)

  // Keep ?q= in step with what is being searched, without piling up history entries.
  useEffect(() => {
    if (query === (params.get('q') ?? '')) return
    setParams(urlParams(query, kind), { replace: true })
  }, [query, kind, params, setParams])

  return (
    <main className="mx-auto flex max-w-content flex-col gap-6 px-4 py-8 lg:px-6 lg:py-12">
      <h1 className="sr-only">Search</h1>
      <div role="group" aria-label="Search for" className="flex gap-1 self-start rounded-control border border-border p-1">
        {kinds.map((option) => {
          const active = option.id === kind
          return (
            <button
              key={option.id}
              type="button"
              aria-pressed={active}
              onClick={() => setParams(urlParams(query, option.id), { replace: true })}
              className={`h-tap rounded-control px-4 text-body ${active ? 'bg-surface-raised font-medium text-text' : 'text-muted'}`}
            >
              {option.label}
            </button>
          )
        })}
      </div>
      <Input
        // Remount on switch: the label and hint change, and focus moves to the field.
        key={kind}
        label={fields[kind].label}
        type="search"
        size="lg"
        hint={fields[kind].hint}
        autoFocus
        autoComplete="off"
        spellCheck={false}
        maxLength={kind === 'people' ? 50 : 100}
        value={text}
        onChange={(event) => setText(event.target.value)}
        className="max-w-prose"
      />
      {query.length >= minSearchLength &&
        (kind === 'people' ? <PeopleResults query={query} /> : <Results query={query} />)}
    </main>
  )
}

function urlParams(query: string, kind: Kind) {
  const next: Record<string, string> = {}
  if (query) next.q = query
  if (kind === 'people') next.type = 'people'
  return next
}

function PeopleResults({ query }: { query: string }) {
  const search = usePeopleSearch(query)

  if (search.isPending) return <PeopleSkeleton />
  if (search.isError) {
    return <ErrorState message="Couldn't search right now." onRetry={() => search.refetch()} retrying={search.isFetching} />
  }
  if (!search.data.items.length) return <EmptyState message="Nobody by that name. Try another." />

  return (
    <ul aria-label={`People matching ${query}`} className="flex max-w-prose flex-col divide-y divide-border">
      {search.data.items.map((person) => (
        <li key={person.id}>
          <PersonRow username={person.username} displayName={person.displayName} />
        </li>
      ))}
    </ul>
  )
}

function Results({ query }: { query: string }) {
  const search = useAlbumSearch(query)

  if (search.isPending) return <ResultsSkeleton />
  if (search.isError) {
    return <ErrorState message="Couldn't search right now." onRetry={() => search.refetch()} retrying={search.isFetching} />
  }

  const { items, partial } = search.data
  return (
    <section aria-label={`Results for ${query}`} className="flex flex-col gap-4">
      {partial && <p className="text-meta text-muted">Some results may be missing.</p>}
      {items.length === 0 ? (
        <EmptyState message="No records in this crate. Try another name." />
      ) : (
        <ul className={grid}>
          {items.map((album, index) => (
            <li key={album.id}>
              <AlbumTile
                eager={index < eagerTiles}
                to={`/albums/${album.id}`}
                title={album.title}
                artist={album.artistCredit}
                src={album.coverUrl}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ResultsSkeleton() {
  return (
    <ul aria-busy="true" aria-label="Searching" className={grid}>
      {Array.from({ length: 12 }, (_, i) => (
        <li key={i}>
          <AlbumTileSkeleton />
        </li>
      ))}
    </ul>
  )
}
