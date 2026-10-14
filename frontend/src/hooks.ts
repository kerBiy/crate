import { useCallback, useState, useSyncExternalStore } from 'react'

/** True while the media query matches, e.g. useMediaQuery('(min-width: 1024px)'); follows changes live. */
export function useMediaQuery(query: string) {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    [query],
  )
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches)
}

/**
 * The element's current height in px, kept up to date as its content changes (a list expanding, an image
 * loading). Pass the returned callback as the element's ref.
 */
export function useElementHeight<T extends HTMLElement>() {
  const [height, setHeight] = useState(0)
  const ref = useCallback((element: T | null) => {
    if (!element) return
    const observer = new ResizeObserver(([entry]) => setHeight(entry.borderBoxSize[0].blockSize))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return [ref, height] as const
}

const subscribeToResize = (onChange: () => void) => {
  window.addEventListener('resize', onChange)
  return () => window.removeEventListener('resize', onChange)
}

/** The window's inner height in px, following resizes. */
export function useWindowHeight() {
  return useSyncExternalStore(subscribeToResize, () => window.innerHeight)
}
