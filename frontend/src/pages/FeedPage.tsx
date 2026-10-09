import { VinylRecord } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { toStars, useFeed } from '../api/queries.ts'
import type { FeedItem } from '../api/types.ts'
import { LoadMore } from '../components/LoadMore.tsx'
import { buttonStyles } from '../components/ui/Button.tsx'
import { FeedRow, FeedRowSkeleton } from '../components/ui/FeedRow.tsx'
import { PageHeader } from '../components/ui/PageHeader.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { formatWhen, nameOf } from '../format.ts'

const page = 'mx-auto flex max-w-content flex-col px-4 pt-6 pb-16 lg:px-6 lg:pt-12'
const list = 'rows flex max-w-prose flex-col'

/** What the people I follow rated, newest first. */
export function FeedPage() {
  return (
    <main className={page}>
      <PageHeader title="Feed" />
      <div className="mt-6 lg:mt-8">
        <Feed />
      </div>
    </main>
  )
}

function Feed() {
  const feed = useFeed()

  if (feed.isPending) {
    return (
      <ul aria-busy="true" aria-label="Loading feed" className={list}>
        {[0, 1, 2, 3].map((i) => (
          <li key={i}>
            <FeedRowSkeleton />
          </li>
        ))}
      </ul>
    )
  }
  if (feed.isError && !feed.data) {
    return <ErrorState message="Couldn't load your feed." onRetry={() => feed.refetch()} retrying={feed.isFetching} />
  }

  // A rating of an album catalog doesn't have can't be drawn; leave it out.
  const items = feed.data.pages.flatMap((p) => p.items).filter((item) => item.album)
  if (!items.length) {
    return (
      <EmptyState
        icon={VinylRecord}
        message="Nothing spinning yet. Follow a friend to fill this crate."
        action={
          <Link to="/search?type=people" className={buttonStyles('primary')}>
            Find friends
          </Link>
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <ul className={list}>
        {items.map((item) => (
          <li key={item.id}>
            <Row item={item} />
          </li>
        ))}
      </ul>
      <LoadMore
        hasMore={feed.hasNextPage}
        loading={feed.isFetchingNextPage}
        failed={feed.isFetchNextPageError}
        onLoad={() => feed.fetchNextPage()}
        error="Couldn't load more of your feed."
      />
    </div>
  )
}

function Row({ item }: { item: FeedItem }) {
  const album = item.album!
  return (
    <FeedRow
      name={nameOf(item.author)}
      username={item.author?.username ?? null}
      albumId={item.albumId}
      title={album.title}
      artist={album.artistCredit}
      coverUrl={album.coverUrl}
      rating={toStars(item.rating)}
      body={item.body}
      at={item.createdAt}
      when={formatWhen(item.createdAt)}
    />
  )
}
