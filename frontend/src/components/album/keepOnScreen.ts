/**
 * Holds the element at its current place on the screen while the page settles after something above or
 * around it changed length (the tracklist, the description): the content re-renders,
 * then the left column may switch between pinned and scrolling a frame later (its height is measured
 * by a ResizeObserver), so it corrects over a few frames.
 */
export function keepOnScreen(element: HTMLElement) {
  const top = element.getBoundingClientRect().top
  let frames = 0
  const settle = () => {
    const moved = element.getBoundingClientRect().top - top
    if (moved !== 0) window.scrollBy(0, moved)
    if (++frames < 4) requestAnimationFrame(settle)
  }
  requestAnimationFrame(settle)
}
