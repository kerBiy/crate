import { MagnifyingGlass, VinylRecord } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { minSearchLength, useAlbumSearch, usePeopleSearch } from '../../api/queries.ts'
import { AlbumTile, AlbumTileSkeleton } from '../../components/ui/AlbumTile.tsx'
import { PageHeader } from '../../components/ui/PageHeader.tsx'
import { PersonRow } from '../../components/ui/PersonRow.tsx'
import { SearchField } from '../../components/ui/SearchField.tsx'
import { SegmentedControl } from '../../components/ui/SegmentedControl.tsx'
import { Skeleton } from '../../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../../components/ui/States.tsx'
import { PeopleSkeleton } from '../../pages/FollowListPage.tsx'

type Kind = 'albums' | 'people'
const kinds: { id: Kind; label: string }[] = [
  { id: 'albums', label: 'Albums' },
  { id: 'people', label: 'People' },
]
const fields: Record<Kind, string> = { albums: 'Album or artist', people: 'Name or username' }

// Fewer, bigger covers: 2 across on a phone, 5 on desktop.
const grid = 'grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 lg:gap-x-6 lg:gap-y-10'
// The first row at the widest grid (two and a half rows on a phone) is on screen at once.
const eagerTiles = 5

function useDebounced<T>(value: T, delay: number) {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return [settled, () => setSettled(value)] as const
}

/** v2 proposal of Search, with real data. Lab only: /lab/v2/search. */
export function SearchPageV2() {
  const [params, setParams] = useSearchParams()
  const kind: Kind = params.get('type') === 'people' ? 'people' : 'albums'
  const [text, setText] = useState(params.get('q') ?? '')
  const [query, searchNow] = useDebounced(text.trim(), 350)

  useEffect(() => {
    if (query === (params.get('q') ?? '')) return
    setParams(urlParams(query, kind), { replace: true })
  }, [query, kind, params, setParams])

  return (
    <main className="mx-auto flex max-w-content flex-col px-4 pt-6 pb-16 lg:px-6 lg:pt-12">
      <PageHeader title="Search">
        <SegmentedControl
          label="Search for"
          options={kinds}
          value={kind}
          onChange={(next) => setParams(urlParams(query, next), { replace: true })}
        />
      </PageHeader>
      <SearchField
        // Remount on switch: the label changes, and focus moves to the field.
        key={kind}
        label={fields[kind]}
        value={text}
        onChange={setText}
        onSubmit={searchNow}
        autoFocus
        autoComplete="off"
        spellCheck={false}
        maxLength={kind === 'people' ? 50 : 100}
        className="mt-6 lg:mt-8"
      />
      <div className="mt-10 lg:mt-12">
        {query.length >= minSearchLength &&
          (kind === 'people' ? <PeopleResults query={query} /> : <Results query={query} />)}
      </div>
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
  if (!search.data.items.length) return <EmptyState icon={MagnifyingGlass} message="Nobody by that name. Try another." />

  return (
    <ul
      aria-label={`People matching ${query}`}
      aria-busy={search.isPlaceholderData}
      className="results rows flex max-w-prose flex-col"
    >
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
  if (items.length === 0) return <EmptyState icon={VinylRecord} message="No records in this crate. Try another name." />

  return (
    <section aria-label={`Results for ${query}`} aria-busy={search.isPlaceholderData} className="results flex flex-col gap-6">
      <p className="tabular text-meta text-muted">
        {items.length} album{items.length === 1 ? '' : 's'}
        {partial && '. Some may be missing.'}
      </p>
      <ul className={grid}>
        {items.map((album, index) => (
          <li key={album.id}>
            <AlbumTile
              eager={index < eagerTiles}
              to={`/lab/v2/album/${album.id}`}
              title={album.title}
              artist={album.artistCredit}
              year={album.year}
              src={album.coverUrl}
            />
          </li>
        ))}
      </ul>
    </section>
  )
}

function ResultsSkeleton() {
  return (
    <div aria-busy="true" aria-label="Searching" className="flex flex-col gap-6">
      <p className="text-meta">
        <Skeleton shape="text" className="w-16" />
      </p>
      <ul aria-hidden="true" className={grid}>
        {Array.from({ length: 10 }, (_, i) => (
          <li key={i}>
            <AlbumTileSkeleton />
          </li>
        ))}
      </ul>
    </div>
  )
}
