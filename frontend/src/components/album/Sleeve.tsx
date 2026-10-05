import { useState, type AnimationEvent } from 'react'
import { coverUrl, type Album } from '../../lab/albums.ts'
import { Cover } from '../ui/Cover.tsx'
import type { AlbumPageState } from './useAlbumPage.ts'
import { Vinyl } from '../ui/Vinyl.tsx'

type SleeveProps = {
  album: Album
  page: AlbumPageState
}

/** Album page cover with the record: slides out once on open, spins on rate, travels on send. */
export function Sleeve({ album, page }: SleeveProps) {
  const [revealed, setRevealed] = useState(false)

  function onRecordEnd(event: AnimationEvent) {
    if (event.target !== event.currentTarget) return
    if (event.animationName === 'record-reveal') setRevealed(true)
    if (event.animationName === 'record-send') page.send.done()
  }

  return (
    <div className="sleeve shrink-0">
      <div
        className="sleeve-record"
        data-reveal={revealed ? undefined : ''}
        data-sending={page.send.on ? '' : undefined}
        onAnimationEnd={onRecordEnd}
      >
        <div
          className="record-spin size-full"
          data-spinning={page.spin.on ? '' : undefined}
          onAnimationEnd={page.spin.done}
        >
          <Vinyl color={album.dominantColor} />
        </div>
      </div>
      <div className="sleeve-cover" data-nudging={page.nudge.on ? '' : undefined} onAnimationEnd={page.nudge.done}>
        <Cover src={coverUrl(album.mbid, 500)} title={album.title} artist={album.artist} />
      </div>
    </div>
  )
}
