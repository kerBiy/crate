import { useCallback, useId, useRef, useState } from 'react'
import { useAlbumDescription } from '../../api/queries.ts'
import { Button } from '../ui/Button.tsx'
import { Skeleton } from '../ui/Skeleton.tsx'
import { keepOnScreen } from './keepOnScreen.ts'

/**
 * The album's introduction under the meta line (DESIGN.md section 8): its English Wikipedia article's
 * first paragraph. Skeleton lines while it loads; nothing at all when there is no article or it failed,
 * since it is a nice-to-have, never an error on the page.
 */
export function AlbumDescription({ albumId }: { albumId: string }) {
  const description = useAlbumDescription(albumId)
  if (description.isPending) return <DescriptionSkeleton />
  const { text, articleUrl } = description.data ?? {}
  if (!text || !articleUrl) return null
  return <Description text={text} articleUrl={articleUrl} />
}

/**
 * The text, clamped to three lines with More to read the rest in place (Less folds it again), then
 * whose it is: "From Wikipedia", linked to the article. The license (CC BY-SA 4.0) is named on /credits
 * (ADR 014). More only shows when the text is longer than three lines at the current width.
 */
export function Description({ text, articleUrl }: { text: string; articleUrl: string }) {
  const [expanded, setExpanded] = useState(false)
  const [clamped, setClamped] = useState(false)
  const textId = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)

  // Measured while folded, again whenever the width changes: whether three lines hide anything.
  const measure = useCallback((element: HTMLParagraphElement | null) => {
    if (!element) return
    const check = () => {
      if (element.dataset.expanded) return
      setClamped(element.scrollHeight > element.clientHeight)
    }
    const observer = new ResizeObserver(check)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  function toggle() {
    // Less: the lines above go, the button stays under the finger.
    if (expanded && buttonRef.current) keepOnScreen(buttonRef.current)
    setExpanded(!expanded)
  }

  return (
    <div className="max-w-prose">
      <p
        id={textId}
        ref={measure}
        data-expanded={expanded || undefined}
        className={`text-body text-text ${expanded ? '' : 'line-clamp-3'}`}
      >
        {text}
      </p>
      {(clamped || expanded) && (
        // Tight: the label lines up with the text, and the focus ring stays clear of the screen's edge.
        <Button ref={buttonRef} variant="ghost" tight aria-expanded={expanded} aria-controls={textId} onClick={toggle}>
          {expanded ? 'Less' : 'More'}
        </Button>
      )}
      <p className={`text-meta text-muted ${clamped || expanded ? '' : 'mt-2'}`}>
        From{' '}
        <a href={articleUrl} target="_blank" rel="noreferrer" className="font-medium text-accent-text underline">
          Wikipedia
        </a>
      </p>
    </div>
  )
}

/** Three lines of text and the credit under them, in the real text's line heights. */
export function DescriptionSkeleton() {
  return (
    <div aria-hidden="true" className="max-w-prose">
      {['w-full', 'w-full', 'w-2/3'].map((width, i) => (
        <p key={i} className="text-body">
          <Skeleton shape="text" className={width} />
        </p>
      ))}
      <p className="mt-2 text-meta">
        <Skeleton shape="text" className="w-1/3" />
      </p>
    </div>
  )
}
