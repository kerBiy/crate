import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from './Button.tsx'

/** How long a toast stays up. Mirrors --duration-toast in index.css. */
export const toastDuration = 2400
/** A toast with an action stays longer, so the action can be reached. Mirrors --duration-toast-action. */
export const toastActionDuration = 6000
/** The exit, before the toast is gone. Mirrors --duration-base. */
const exitDuration = 300

type ToastAction = {
  label: string
  run: () => void
  /** Move keyboard focus to the action: for when the control that was used has just disappeared. */
  focus?: boolean
}

export type ToastOptions = {
  action?: ToastAction
  /** The toast closed without its action being used: timed out, or replaced by another toast. */
  onClose?: () => void
}

type Current = { id: number; message: string; action?: ToastAction; leaving: boolean }

/**
 * One message at a time; a new one replaces the old and restarts the timer. The timer pauses while
 * the toast is hovered or focused, so an action can't vanish from under the pointer or the keyboard.
 */
export function useToast() {
  const [current, setCurrent] = useState<Current | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const exit = useRef<number | undefined>(undefined)
  const onClose = useRef<(() => void) | undefined>(undefined)
  const remaining = useRef(0)
  const startedAt = useRef(0)
  const nextId = useRef(0)
  // Shown and not closing: only then can hovering or focus hold it.
  const open = useRef(false)
  const holding = useRef(false)

  const close = useCallback((viaAction: boolean) => {
    open.current = false
    holding.current = false
    window.clearTimeout(timer.current)
    const closed = onClose.current
    onClose.current = undefined
    if (!viaAction) closed?.()
    setCurrent((toast) => (toast ? { ...toast, leaving: true } : null))
    window.clearTimeout(exit.current)
    exit.current = window.setTimeout(() => setCurrent(null), exitDuration)
  }, [])

  const run = useCallback((ms: number) => {
    window.clearTimeout(timer.current)
    remaining.current = ms
    startedAt.current = Date.now()
    timer.current = window.setTimeout(() => close(false), ms)
  }, [close])

  const show = useCallback(
    (message: string, options: ToastOptions = {}) => {
      // The toast being replaced closes without its action.
      onClose.current?.()
      onClose.current = options.onClose
      window.clearTimeout(exit.current)
      open.current = true
      setCurrent({ id: nextId.current++, message, action: options.action, leaving: false })
      run(options.action ? toastActionDuration : toastDuration)
    },
    [run],
  )

  /** Hold the toast while it's hovered or focused; let it go with the time it had left. */
  const hold = useCallback(
    (held: boolean) => {
      if (!open.current || held === holding.current) return
      holding.current = held
      if (held) {
        window.clearTimeout(timer.current)
        remaining.current = Math.max(0, remaining.current - (Date.now() - startedAt.current))
      } else {
        run(Math.max(remaining.current, toastDuration / 2))
      }
    },
    [run],
  )

  /** Escape on the toast: close it now, as if its time had run out (onClose runs). */
  const expire = useCallback(() => {
    if (open.current) close(false)
  }, [close])

  /** Close it now, as if its action had been used: onClose doesn't run. */
  const dismiss = useCallback(() => {
    if (open.current) close(true)
  }, [close])

  const act = useCallback(() => {
    current?.action?.run()
    close(true)
  }, [current, close])

  // Leaving the page counts as the toast closing (for example: an Undo window ends).
  useEffect(
    () => () => {
      window.clearTimeout(timer.current)
      window.clearTimeout(exit.current)
      onClose.current?.()
    },
    [],
  )

  return { current, show, hold, act, dismiss, expire }
}

export type Toast = ReturnType<typeof useToast>

/** The floating confirmation itself, with at most one action. */
export function ToastMessage({
  children,
  action,
  onAction,
  onHold,
  onEscape,
  leaving = false,
}: {
  children: string
  action?: ToastAction
  onAction?: () => void
  /** Hovered or focused (true), then let go (false). */
  onHold?: (held: boolean) => void
  onEscape?: () => void
  leaving?: boolean
}) {
  return (
    <div
      data-leaving={leaving ? '' : undefined}
      onPointerEnter={() => onHold?.(true)}
      onPointerLeave={() => onHold?.(false)}
      // Only keyboard focus holds it: focus that arrived with a click must not keep it up forever.
      onFocus={(event) => event.target.matches(':focus-visible') && onHold?.(true)}
      onBlur={() => onHold?.(false)}
      onKeyDown={(event) => event.key === 'Escape' && onEscape?.()}
      className={`toast flex items-center gap-2 rounded-control border border-border bg-surface-raised text-body text-text shadow-float ${
        action ? 'pointer-events-auto py-1 pr-1 pl-4' : 'px-4 py-3'
      }`}
    >
      <p>{children}</p>
      {action && (
        <Button variant="ghost" autoFocus={action.focus} onClick={onAction} className="px-3">
          {action.label}
        </Button>
      )}
    </div>
  )
}

/** Live region pinned above the tab bar (mobile) or the bottom edge (desktop). Render it once per page. */
export function ToastRegion({ toast }: { toast: Toast }) {
  const { current } = toast
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-4 bottom-tabbar-safe z-30 flex justify-center pb-4 lg:bottom-0 lg:pb-6"
    >
      {current && (
        <ToastMessage
          key={current.id}
          action={current.action}
          onAction={toast.act}
          onHold={current.action ? toast.hold : undefined}
          onEscape={toast.expire}
          leaving={current.leaving}
        >
          {current.message}
        </ToastMessage>
      )}
    </div>
  )
}
