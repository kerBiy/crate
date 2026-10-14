const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })

/** "now", "5m", "3h", "2d" within a week; then a date: "4 Oct 2026". */
export function formatWhen(iso: string, now = Date.now()) {
  const seconds = Math.max(0, (now - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return 'now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h`
  if (seconds < 7 * 86_400) return `${Math.floor(seconds / 86_400)}d`
  return dateFormat.format(new Date(iso))
}

/** "1 follower", "12 followers". */
export function count(n: number, one: string, many = `${one}s`) {
  return `${n} ${n === 1 ? one : many}`
}

/** The name to show for someone: display name, or the username without one. */
export function nameOf(user: { username: string; displayName: string | null } | null) {
  return user ? user.displayName || user.username : 'Someone'
}

const compactFormat = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })

/** 950 → "950", 12_345 → "12.3k", 1_781_830 → "1.8m". */
export function compact(n: number) {
  return compactFormat.format(n).toLowerCase()
}

/** A track's length: 284_400 → "4:44", 3_725_000 → "1:02:05". */
export function trackLength(ms: number) {
  const total = Math.round(ms / 1000)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = String(total % 60).padStart(2, '0')
  return hours ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`
}

/** An album's length, to the minute: 3_207_156 → "53 min", 7_312_323 → "2 h 2 min", 3_600_000 → "1 h". */
export function albumLength(ms: number) {
  const minutes = Math.max(1, Math.round(ms / 60_000))
  const hours = Math.floor(minutes / 60)
  if (!hours) return `${minutes} min`
  return minutes % 60 ? `${hours} h ${minutes % 60} min` : `${hours} h`
}

/** An album's length in words, for reading rather than scanning: "42 minutes", "1 hour 12 minutes", "2 hours". */
export function albumLengthWords(ms: number) {
  const minutes = Math.max(1, Math.round(ms / 60_000))
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  const h = hours ? `${hours} ${hours === 1 ? 'hour' : 'hours'}` : ''
  const m = rest ? `${rest} ${rest === 1 ? 'minute' : 'minutes'}` : ''
  return [h, m].filter(Boolean).join(' ')
}
