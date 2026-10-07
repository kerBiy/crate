import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { minSearchLength, useAlbumSearch } from '../api/queries.ts'
import { AlbumTile, AlbumTileSkeleton } from '../components/ui/AlbumTile.tsx'
import { Input } from '../components/ui/Input.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'

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

/** One big field, results as a cover grid. The query lives in the URL, so Back restores it. */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const [text, setText] = useState(params.get('q') ?? '')
  // Long enough that a pause mid-word doesn't fire a search: every new query may reach MusicBrainz.
  const query = useDebounced(text.trim(), 350)

  // Keep ?q= in step with what is being searched, without piling up history entries.
  useEffect(() => {
    if (query === (params.get('q') ?? '')) return
    setParams(query ? { q: query } : {}, { replace: true })
  }, [query, params, setParams])

  return (
    <main className="mx-auto flex max-w-content flex-col gap-8 px-4 py-8 lg:px-6 lg:py-12">
      <h1 className="sr-only">Search</h1>
      <Input
        label="Search albums"
        type="search"
        size="lg"
        hint="Album or artist"
        autoFocus
        autoComplete="off"
        spellCheck={false}
        maxLength={100}
        value={text}
        onChange={(event) => setText(event.target.value)}
        className="max-w-prose"
      />
      {query.length >= minSearchLength && <Results query={query} />}
    </main>
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
