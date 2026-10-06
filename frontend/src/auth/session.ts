import { useSyncExternalStore } from 'react'

// MVP shortcut (SPEC 3.6): one long-lived access token in localStorage. Any script on the page
// can read it and the server can't revoke it before it expires. Phase 2 moves to a short token
// in memory plus a refresh token in an httpOnly cookie.

export type Session = { accessToken: string; expiresAt: string }

const key = 'crate-session'
const listeners = new Set<() => void>()

// Cached so useSyncExternalStore gets the same object until the stored value changes.
let cachedRaw: string | null = null
let cached: Session | null = null

function read(): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** The stored session, or null if there is none or it has expired. */
export function getSession(): Session | null {
  const raw = read()
  if (raw !== cachedRaw) {
    cachedRaw = raw
    cached = parse(raw)
  }
  if (cached && Date.parse(cached.expiresAt) <= Date.now()) return null
  return cached
}

export function setSession(session: Session) {
  try {
    localStorage.setItem(key, JSON.stringify(session))
  } catch {
    // Storage blocked: nothing we can do, the user stays signed out.
  }
  notify()
}

export function clearSession() {
  try {
    localStorage.removeItem(key)
  } catch {
    // Ignore: there is nothing to remove.
  }
  notify()
}

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Re-renders when the user signs in or out, in this tab or another one. */
export function useSession() {
  return useSyncExternalStore(subscribe, getSession)
}

function notify() {
  listeners.forEach((listener) => listener())
}

function parse(raw: string | null): Session | null {
  if (!raw) return null
  try {
    const value = JSON.parse(raw)
    return typeof value?.accessToken === 'string' && typeof value?.expiresAt === 'string' ? value : null
  } catch {
    return null
  }
}

// Another tab signed in or out: the storage event fires here, not in the tab that wrote.
window.addEventListener('storage', (event) => {
  if (event.key === key || event.key === null) notify()
})
