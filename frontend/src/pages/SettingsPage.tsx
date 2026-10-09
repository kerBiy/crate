import { Link } from 'react-router'
import { logout } from '../api/queryClient.ts'
import { SectionTitle } from '../components/album/Reviews.tsx'
import { Button } from '../components/ui/Button.tsx'
import { SegmentedControl } from '../components/ui/SegmentedControl.tsx'
import { useTheme, type ThemeChoice } from '../theme.ts'

const page = 'mx-auto flex max-w-content flex-col gap-8 px-4 py-8 lg:px-6 lg:py-12'
const themes: { id: ThemeChoice; label: string }[] = [
  { id: 'dark', label: 'Dark' },
  { id: 'light', label: 'Light' },
  { id: 'system', label: 'Match system' },
]

/** /settings, reached from my own profile: theme and log out. Nothing to load, so no data states. */
export function SettingsPage() {
  const { choice, choose } = useTheme()
  return (
    <main className={page}>
      <header className="flex flex-col">
        <Link to="/profile" className="flex min-h-tap items-center self-start rounded-control text-meta text-muted">
          Your profile
        </Link>
        <h1 className="font-display text-h3 font-semibold text-text">Settings</h1>
      </header>
      <section className="flex flex-col">
        <SectionTitle>Theme</SectionTitle>
        <SegmentedControl label="Theme" options={themes} value={choice} onChange={choose} />
      </section>
      <section className="flex flex-col items-start">
        <SectionTitle>Account</SectionTitle>
        <Button onClick={logout}>Log out</Button>
      </section>
    </main>
  )
}
