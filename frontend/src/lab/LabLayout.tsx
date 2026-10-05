import { Outlet, useLocation } from 'react-router'
import { AppShell, type NavLinks, type Section } from '../components/ui/AppShell.tsx'
import { DevPanel } from './DevPanel.tsx'

const labLinks: NavLinks = { search: '/lab/grid', feed: '/lab/feed', profile: '/lab/profile' }

/** Design lab: the real app shell around the pages, lab controls tucked in a corner. */
export function LabLayout() {
  const { pathname } = useLocation()
  // Album pages are reached from search, so they keep Search active.
  const section: Section = pathname.startsWith(labLinks.feed)
    ? 'feed'
    : pathname.startsWith(labLinks.profile)
      ? 'profile'
      : 'search'

  return (
    <AppShell links={labLinks} current={section}>
      <Outlet />
      <DevPanel />
    </AppShell>
  )
}
