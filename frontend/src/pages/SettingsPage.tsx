import { Link } from 'react-router'
import { logout } from '../api/queryClient.ts'
import { Button } from '../components/ui/Button.tsx'
import { PageHeader, pageFrame } from '../components/ui/PageHeader.tsx'
import { SectionHeading } from '../components/ui/SectionHeading.tsx'
import { SegmentedControl } from '../components/ui/SegmentedControl.tsx'
import { useTheme, type ThemeChoice } from '../theme.ts'

const themes: { id: ThemeChoice; label: string }[] = [
  { id: 'dark', label: 'Dark' },
  { id: 'light', label: 'Light' },
  { id: 'system', label: 'Match system' },
]

/** /settings, reached from my own profile: theme and log out. Nothing to load, so no data states. */
export function SettingsPage() {
  const { choice, choose } = useTheme()
  return (
    <main className={pageFrame}>
      <header className="flex flex-col">
        <Link to="/profile" className="flex min-h-tap items-center self-start rounded-control text-body text-muted">
          Your profile
        </Link>
        <PageHeader title="Settings" />
      </header>
      <section className="mt-8 flex flex-col items-start lg:mt-12">
        <SectionHeading>Theme</SectionHeading>
        <SegmentedControl label="Theme" options={themes} value={choice} onChange={choose} />
      </section>
      <section className="mt-12 flex flex-col items-start lg:mt-16">
        <SectionHeading>Account</SectionHeading>
        <Button onClick={logout}>Log out</Button>
      </section>
    </main>
  )
}
