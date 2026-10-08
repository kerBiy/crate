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
