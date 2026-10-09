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
  /** Desktop: stays in place while its column scrolls (the page's flex row needs items-start). */
  pinned?: boolean
}

/**
 * Album page cover with the record: slides out once the cover has loaded, and reacts to actions
 * if given. 280px on mobile, 360px on desktop. The cover is above the fold, so it loads eagerly.
 */
export function Sleeve({ title, artist, src, color, motion, pinned = false }: SleeveProps) {
  const [coverReady, setCoverReady] = useState(false)
  const [revealed, setRevealed] = useState(false)

  function onRecordEnd(event: AnimationEvent) {
    if (event.target !== event.currentTarget) return
    if (event.animationName === 'record-reveal') setRevealed(true)
    if (event.animationName === 'record-send') motion?.send.done()
  }

  return (
    <div className={`sleeve shrink-0 ${pinned ? 'sleeve-pinned' : ''}`}>
      <div
        className="sleeve-record"
        // The record stays in the sleeve until the cover is in, then slides out once.
        data-reveal={revealed ? undefined : coverReady ? 'run' : 'wait'}
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
        <Cover src={src} title={title} artist={artist} eager size="lg" onSettled={() => setCoverReady(true)} />
      </div>
    </div>
  )
}
