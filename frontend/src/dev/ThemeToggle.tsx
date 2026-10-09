import { useTheme } from '../theme.ts'

const segment = 'flex h-tap flex-1 items-center justify-center rounded-control px-3 text-body'
const on = `${segment} bg-surface font-medium text-text`
const off = `${segment} text-muted`

/** Dark / Light segmented switch for the dev tools. The app's own setting comes later. */
export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  return (
    <div className="flex rounded-control border border-border-strong p-1" role="group" aria-label="Theme">
      <button type="button" onClick={theme === 'light' ? toggle : undefined} aria-pressed={theme === 'dark'} className={theme === 'dark' ? on : off}>
        Dark
      </button>
      <button type="button" onClick={theme === 'dark' ? toggle : undefined} aria-pressed={theme === 'light'} className={theme === 'light' ? on : off}>
        Light
      </button>
    </div>
  )
}
