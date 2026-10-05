import { useState } from 'react'

export type Theme = 'dark' | 'light'

const key = 'crate-theme'

function current(): Theme {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'
}

/** Theme lives on <html data-theme>; dark is the default. index.html applies it before paint. */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(current)

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    if (next === 'light') document.documentElement.dataset.theme = 'light'
    else delete document.documentElement.dataset.theme
    try {
      localStorage.setItem(key, next)
    } catch {
      // Storage can be blocked; the theme still applies for this visit.
    }
    setTheme(next)
  }

  return { theme, toggle }
}
