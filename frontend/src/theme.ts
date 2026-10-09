import { useState } from 'react'

/** What the person chose. "system" follows the device's appearance, live. */
export type ThemeChoice = 'dark' | 'light' | 'system'

const key = 'crate-theme'
const deviceLight = window.matchMedia('(prefers-color-scheme: light)')

/** The saved choice; dark when there is none (DESIGN.md section 4.1). */
function saved(): ThemeChoice {
  try {
    const value = localStorage.getItem(key)
    return value === 'light' || value === 'system' ? value : 'dark'
  } catch {
    return 'dark'
  }
}

/** Theme lives on <html data-theme>: "light" or absent (dark). index.html applies it before paint. */
function apply(choice: ThemeChoice) {
  const light = choice === 'light' || (choice === 'system' && deviceLight.matches)
  if (light) document.documentElement.dataset.theme = 'light'
  else delete document.documentElement.dataset.theme
}

/** Once, at startup: with "Match system", follow the device when it switches between light and dark. */
export function followDeviceTheme() {
  deviceLight.addEventListener('change', () => {
    if (saved() === 'system') apply('system')
  })
}

export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(saved)

  function choose(next: ThemeChoice) {
    apply(next)
    try {
      localStorage.setItem(key, next)
    } catch {
      // Storage can be blocked; the theme still applies for this visit.
    }
    setChoice(next)
  }

  return { choice, choose }
}
