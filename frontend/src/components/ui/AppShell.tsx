import { House, MagnifyingGlass, User, type Icon } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { useMe } from '../../api/queries.ts'
import { Avatar } from './Avatar.tsx'

export type Section = 'search' | 'feed' | 'profile'

export type NavLinks = Record<Section, string>

/** The app's real sections. The design lab passes its own paths. */
export const appLinks: NavLinks = { search: '/search', feed: '/feed', profile: '/profile' }

type Tab = { id: Section; label: string; icon: Icon }

const desktopOrder: Tab[] = [
  { id: 'search', label: 'Search', icon: MagnifyingGlass },
  { id: 'feed', label: 'Feed', icon: House },
  { id: 'profile', label: 'Profile', icon: User },
]

// DESIGN.md section 8: mobile tabs are Feed, Search, Profile.
const mobileOrder = [desktopOrder[1], desktopOrder[0], desktopOrder[2]]

type AppShellProps = {
  children: ReactNode
  links?: NavLinks
  /** Active section. By default it comes from the URL. */
  current?: Section
  /** Signed-in user's name, for the avatar. */
  userName?: string
}

/** Page frame: navigation around the content, room kept for the mobile tab bar. */
export function AppShell({ children, links = appLinks, current, userName }: AppShellProps) {
  const { pathname } = useLocation()
  const active = current ?? desktopOrder.find((tab) => pathname.startsWith(links[tab.id]))?.id

  return (
    <div className="min-h-dvh overflow-x-clip pb-tabbar-safe lg:pb-0">
      <AppNav links={links} current={active} userName={userName} />
      {children}
    </div>
  )
}

/**
 * AppShell as a layout route, for signed-in pages. Profiles live at /u/:username: mine (and my
 * Settings) is the Profile tab, someone else's is no tab. An album page keeps the tab it was reached
 * from, Search when opened directly (DESIGN.md section 8).
 */
export function AppShellLayout() {
  const location = useLocation()
  // The v2 lab pages stand in for Search and the album page (development only, temporary).
  const pathname = import.meta.env.DEV
    ? location.pathname.replace(/^\/lab\/v2\/album\//, '/albums/').replace(/^\/lab\/v2\//, '/')
    : location.pathname
  const me = useMe()
  const profileOf = pathname.match(/^\/u\/([^/]+)/)?.[1]
  const mine = Boolean(profileOf && me.data && decodeURIComponent(profileOf).toLowerCase() === me.data.username)
  const own: Section | undefined =
    mine || pathname.startsWith('/settings') || pathname.startsWith(appLinks.profile)
      ? 'profile'
      : pathname.startsWith(appLinks.feed)
        ? 'feed'
        : pathname.startsWith(appLinks.search)
          ? 'search'
          : undefined
  // The last tab visited. Updated while rendering (React's pattern for state derived from a change),
  // so an album page knows it at once.
  const [cameFrom, setCameFrom] = useState<Section>('search')
  if (own && own !== cameFrom) setCameFrom(own)
  const current = pathname.startsWith('/albums/') ? cameFrom : own
  return (
    <AppShell current={current} userName={me.data?.username}>
      <Outlet />
    </AppShell>
  )
}

/** Desktop: top bar with wordmark, sections and avatar. Mobile: wordmark on top, tabs at the bottom. */
function AppNav({ links, current, userName = 'You' }: { links: NavLinks; current?: Section; userName?: string }) {
  return (
    <>
      <header className="app-header sticky top-0 z-10 border-b border-border bg-bg">
        <div className="mx-auto flex h-12 max-w-content items-center gap-8 px-4 lg:h-16 lg:px-6">
          <Link to={links.feed} className="font-display text-h3 font-bold text-text">
            crate
          </Link>
          <nav aria-label="Main" className="hidden lg:block">
            <ul className="flex gap-1">
              {desktopOrder.map((tab) => (
                <li key={tab.id}>
                  <Link
                    to={links[tab.id]}
                    aria-current={tab.id === current ? 'page' : undefined}
                    className={`flex h-tap items-center rounded-control px-3 text-body active:bg-pressed ${
                      tab.id === current ? 'bg-surface font-medium text-text' : 'text-muted'
                    }`}
                  >
                    {tab.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <Link to={links.profile} aria-label="Your profile" className="ml-auto hidden rounded-full lg:block">
            <Avatar name={userName.toUpperCase()} />
          </Link>
        </div>
      </header>

      <nav aria-label="Main" className="tabbar fixed inset-x-0 bottom-0 z-10 border-t border-border bg-bg lg:hidden">
        <ul className="grid h-tabbar grid-cols-3">
          {mobileOrder.map((tab) => {
            const active = tab.id === current
            const TabIcon = tab.icon
            return (
              <li key={tab.id}>
                <Link
                  to={links[tab.id]}
                  aria-current={active ? 'page' : undefined}
                  className={`flex h-full flex-col items-center justify-center gap-1 text-meta active:bg-pressed ${
                    active ? 'font-medium text-text' : 'text-muted'
                  }`}
                >
                  <TabIcon size="1.5em" weight={active ? 'fill' : 'regular'} className={active ? 'text-accent' : ''} />
                  {tab.label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>
    </>
  )
}
