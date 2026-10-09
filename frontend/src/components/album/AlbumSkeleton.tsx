import { Skeleton } from '../ui/Skeleton.tsx'

/** Loading shape for the album page: the sleeve's square and the title column's lines. */
export function AlbumSkeleton() {
  return (
    <div
      aria-busy="true"
      aria-label="Loading album"
      className="mx-auto flex max-w-content flex-col gap-8 px-4 pt-6 pb-16 lg:flex-row lg:items-start lg:gap-16 lg:px-6 lg:pt-12"
    >
      <div className="sleeve">
        <Skeleton shape="block" className="aspect-square w-full rounded-cover-lg" />
      </div>
      <div className="flex flex-1 flex-col gap-4">
        <Skeleton className="h-12 w-2/3 lg:h-16" />
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-4 w-1/4" />
        <Skeleton shape="block" className="mt-8 h-12 w-1/2" />
      </div>
    </div>
  )
}
