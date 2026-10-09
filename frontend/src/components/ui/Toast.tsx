import { useCallback, useEffect, useRef, useState } from 'react'

/** How long a toast stays up. Mirrors --duration-toast in index.css. */
export const toastDuration = 2400

/** One message at a time; a new one replaces the old and restarts the timer. */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback((next: string) => {
    setMessage(next)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMessage(null), toastDuration)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return { message, show }
}

/** The floating confirmation itself. */
export function ToastMessage({ children }: { children: string }) {
  return (
    <p className="rounded-control border border-border bg-surface-raised px-4 py-3 text-body text-text shadow-float">
      {children}
    </p>
  )
}

/** Live region pinned above the tab bar (mobile) or the bottom edge (desktop). Render it once per page. */
export function ToastRegion({ message }: { message: string | null }) {
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-4 bottom-tabbar-safe z-30 flex justify-center pb-4 lg:bottom-0 lg:pb-6"
    >
      {message && <ToastMessage>{message}</ToastMessage>}
    </div>
  )
}
