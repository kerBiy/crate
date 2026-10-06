import { useState, type AnimationEvent } from 'react'
import { Cover } from '../ui/Cover.tsx'
import { Vinyl } from '../ui/Vinyl.tsx'
import type { AlbumPageState } from './useAlbumPage.ts'

type SleeveProps = {
  title: string
  artist: string
  src?: string
  /** Album's dominant color for the record label. */
  color?: string
  /** Action animations (spin on rate, travel on send, nudge on listen later). Omit for none. */
  motion?: Pick<AlbumPageState, 'spin' | 'send' | 'nudge'>
}

/** Album page cover with the record: slides out once on open, and reacts to actions if given. */
export function Sleeve({ title, artist, src, color, motion }: SleeveProps) {
  const [revealed, setRevealed] = useState(false)

  function onRecordEnd(event: AnimationEvent) {
    if (event.target !== event.currentTarget) return
    if (event.animationName === 'record-reveal') setRevealed(true)
    if (event.animationName === 'record-send') motion?.send.done()
  }

  return (
    <div className="sleeve shrink-0">
      <div
        className="sleeve-record"
        data-reveal={revealed ? undefined : ''}
        data-sending={motion?.send.on ? '' : undefined}
        onAnimationEnd={onRecordEnd}
      >
        <div
          className="record-spin size-full"
          data-spinning={motion?.spin.on ? '' : undefined}
          onAnimationEnd={motion?.spin.done}
        >
          <Vinyl color={color} />
        </div>
      </div>
      <div className="sleeve-cover" data-nudging={motion?.nudge.on ? '' : undefined} onAnimationEnd={motion?.nudge.done}>
        <Cover src={src} title={title} artist={artist} />
      </div>
    </div>
  )
}
