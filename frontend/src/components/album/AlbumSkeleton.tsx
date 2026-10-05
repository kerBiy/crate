/** Loading shape for the album page: square cover block and text lines. */
export function AlbumSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading album" className="mx-auto max-w-content px-4 py-12 lg:px-6">
      <div className="flex flex-col gap-8 lg:flex-row lg:gap-12">
        <div className="sleeve">
          <div className="skeleton aspect-square w-full rounded-cover" />
        </div>
        <div className="flex flex-1 flex-col gap-4">
          <span className="skeleton h-12 w-2/3 rounded-cover" />
          <span className="skeleton h-4 w-1/3 rounded-cover" />
          <span className="skeleton mt-8 h-tap w-full rounded-control lg:w-1/2" />
          <span className="skeleton h-8 w-1/2 rounded-cover lg:w-1/3" />
        </div>
      </div>
      <div className="mt-16 flex max-w-prose flex-col gap-3">
        <span className="skeleton h-4 w-1/4 rounded-cover" />
        <span className="skeleton h-3 w-full rounded-cover" />
        <span className="skeleton h-3 w-5/6 rounded-cover" />
      </div>
    </div>
  )
}
