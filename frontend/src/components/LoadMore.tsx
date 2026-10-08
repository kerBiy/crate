import { Button } from './ui/Button.tsx'
import { ErrorState } from './ui/States.tsx'

type LoadMoreProps = {
  hasMore: boolean
  loading: boolean
  failed: boolean
  onLoad: () => void
  /** Button text: "Show more", "Show more ratings". */
  label?: string
  /** What failed, when the next page doesn't come: "Couldn't load more ratings." */
  error: string
}

/** Under a paged list: the button for the next page, or why it didn't come. Nothing on the last page. */
export function LoadMore({ hasMore, loading, failed, onLoad, label = 'Show more', error }: LoadMoreProps) {
  if (failed) return <ErrorState message={error} onRetry={onLoad} retrying={loading} />
  if (!hasMore) return null
  return (
    <div>
      <Button onClick={onLoad} loading={loading}>
        {label}
      </Button>
    </div>
  )
}
