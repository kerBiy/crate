import { BookmarkSimple, PaperPlaneTilt } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Avatar } from '../ui/Avatar.tsx'
import { Button } from '../ui/Button.tsx'
import { RatingInput } from '../ui/RatingInput.tsx'
import type { AlbumPageState } from './useAlbumPage.ts'

type RateControlProps = {
  title: string
  value: number
  /** Omit while rating isn't available: the stars show, disabled. */
  onRate?: (value: number) => void
}

export function RateControl({ title, value, onRate }: RateControlProps) {
  const note = !onRate ? 'Rating opens soon' : value ? 'Rated' : 'Rate'
  return (
    <div className="flex items-center gap-3">
      <RatingInput label={`Rate ${title}`} value={value} onRate={onRate ?? (() => {})} disabled={!onRate} />
      <span className="text-meta text-muted">{note}</span>
    </div>
  )
}

export function ListenLaterButton({ page, className = '' }: { page: AlbumPageState; className?: string }) {
  return (
    <Button
      aria-pressed={page.listenLater}
      onClick={page.toggleListenLater}
      icon={<BookmarkSimple size="1.25em" weight={page.listenLater ? 'fill' : 'regular'} className="text-accent" />}
      className={className}
    >
      Listen later
    </Button>
  )
}

type SendButtonProps = { page: AlbumPageState; friends: string[]; className?: string }

export function SendButton({ page, friends, className = '' }: SendButtonProps) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    root.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus()
    function onPointer(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [open])

  function close() {
    setOpen(false)
    trigger.current?.focus()
  }

  return (
    <div ref={root} className={`relative ${className}`} onKeyDown={(event) => event.key === 'Escape' && close()}>
      <Button
        ref={trigger}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        icon={<PaperPlaneTilt size="1.25em" className="text-accent" />}
        className="w-full"
      >
        Send to a friend
      </Button>
      {open && (
        <div
          role="menu"
          aria-label="Send to"
          className="absolute right-0 z-20 mt-2 flex w-full min-w-max flex-col rounded-control border border-border bg-surface-raised p-1 shadow-float sm:left-0 sm:right-auto"
        >
          {friends.map((name) => (
            <button
              key={name}
              type="button"
              role="menuitem"
              onClick={() => {
                close()
                page.sendTo(name)
              }}
              className="flex h-tap items-center gap-3 rounded-control px-3 text-left text-body text-text focus-visible:bg-surface"
            >
              <Avatar name={name} size="sm" />
              {name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
