import { SlidersHorizontal, X } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ThemeToggle } from '../dev/ThemeToggle.tsx'
import { albums } from './albums.ts'

const segment = 'flex h-tap flex-1 items-center justify-center rounded-control px-2 text-body'
const on = `${segment} bg-surface font-medium text-text`
const off = `${segment} text-muted`

/** Collapsed corner panel with the design-lab controls: page, album, theme. */
export function DevPanel() {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const { mbid } = useParams()
  const { search } = useLocation()
  const navigate = useNavigate()
  const onGrid = !mbid
  const album = mbid ?? albums[0].mbid

  useEffect(() => {
    if (!open) return
    function onPointer(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [open])

  return (
    <div
      ref={root}
      onKeyDown={(event) => event.key === 'Escape' && setOpen(false)}
      className="fixed right-4 bottom-tabbar-safe z-30 flex flex-col items-end gap-2 pb-4 lg:right-6 lg:bottom-0 lg:pb-6"
    >
      {open && (
        <section
          id="dev-panel"
          aria-label="Design lab"
          className="flex w-panel flex-col gap-4 rounded-sheet border border-border bg-surface-raised p-4 shadow-float"
        >
          <h2 className="text-meta text-muted">Design lab</h2>

          <div className="flex rounded-control border border-border-strong p-1" role="group" aria-label="Page">
            <Link to="/lab/grid" className={onGrid ? on : off} aria-current={onGrid ? 'page' : undefined}>
              Grid
            </Link>
            <Link to={`/lab/album/${album}`} className={onGrid ? off : on} aria-current={onGrid ? undefined : 'page'}>
              Album
            </Link>
          </div>

          {!onGrid && (
            <label className="flex flex-col gap-1 text-meta text-muted">
              Album
              <select
                value={album}
                onChange={(event) => navigate(`/lab/album/${event.target.value}${search}`)}
                className="h-tap rounded-control border border-border-strong bg-surface px-3 text-body text-text"
              >
                {albums.map((a) => (
                  <option key={a.mbid} value={a.mbid}>
                    {a.title}
                  </option>
                ))}
              </select>
            </label>
          )}

          <ThemeToggle />
        </section>
      )}
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-controls="dev-panel"
        aria-label={open ? 'Close design lab' : 'Open design lab'}
        className="flex size-tap items-center justify-center rounded-full border border-border bg-surface-raised text-h4 text-text shadow-float"
      >
        {open ? <X size="1em" /> : <SlidersHorizontal size="1em" />}
      </button>
    </div>
  )
}
