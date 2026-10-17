import { House, MagnifyingGlass, User, type Icon } from '@phosphor-icons/react'
import { useCallback, useState, type MouseEvent, type ReactNode } from 'react'
import { Link, Outlet, ScrollRestoration, useLocation, type Location } from 'react-router'
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

/**
 * The tab whose own page this path is: /feed, /search, or my profile at /u/:me. Albums, other
 * people, follower lists, Settings and Credits are pages inside a tab or outside any, not the tab's own page.
 */
function tabRootOf(pathname: string, me?: string): Section | undefined {
  if (pathname === appLinks.feed) return 'feed'
  if (pathname === appLinks.search) return 'search'
  const profileOf = pathname.match(/^\/u\/([^/]+)\/?$/)?.[1]
  if (profileOf && me && decodeURIComponent(profileOf).toLowerCase() === me) return 'profile'
  return undefined
}

type AppShellProps = {
  children: ReactNode
  links?: NavLinks
  /** Active section. By default it comes from the URL. */
  current?: Section
  /** Signed-in user's name, for the avatar. */
  userName?: string
  /** The tab whose own page is showing: tapping that tab scrolls to the top instead of navigating. */
  atRoot?: Section
}

/** Page frame: navigation around the content, room kept for the mobile tab bar. */
export function AppShell({ children, links = appLinks, current, userName, atRoot }: AppShellProps) {
  const { pathname } = useLocation()
  const active =
    current ??
    desktopOrder.find((tab) => pathname === links[tab.id] || pathname.startsWith(`${links[tab.id]}/`))?.id

  return (
    <div className="min-h-dvh overflow-x-clip pb-tabbar-safe lg:pb-0">
      <AppNav links={links} current={active} userName={userName} atRoot={atRoot} />
      {children}
    </div>
  )
}

/**
 * AppShell as a layout route, for signed-in pages. Profiles live at /u/:username: mine (and my
 * Settings) is the Profile tab, someone else's is no tab. An album page keeps the tab it was reached
 * from, Search when opened directly (DESIGN.md section 8).
 *
 * Scroll (DESIGN.md section 8, "Each tab keeps its place"): ScrollRestoration saves the scroll
 * position under a key before every navigation and puts it back when a location with that key
 * opens again, by Back or by a link. Its default key is the history entry, so a new page starts at
 * the top and Back returns to where you were. A tab's own page gets one fixed key instead, so the
 * tab comes back to its own place however it is reached.
 */
export function AppShellLayout() {
  const { pathname, search } = useLocation()
  const me = useMe()
  const myName = me.data?.username
  const profileOf = pathname.match(/^\/u\/([^/]+)/)?.[1]
  const mine = Boolean(profileOf && me.data && decodeURIComponent(profileOf).toLowerCase() === me.data.username)
  const own: Section | undefined =
    mine || pathname.startsWith('/settings') || pathname.startsWith('/credits') || pathname.startsWith(appLinks.profile)
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
  // Search keeps its query in the URL, so its tab returns to the last search, not an empty page.
  // Profile goes straight to /u/:me once it's known: the /profile redirect is one more navigation,
  // and one that (run twice in StrictMode) can save the blank hop's scroll over Profile's place.
  const [searchUrl, setSearchUrl] = useState(appLinks.search)
  if (pathname === appLinks.search && pathname + search !== searchUrl) setSearchUrl(pathname + search)
  const scrollKey = useCallback(
    (location: Location) => {
      const tab = tabRootOf(location.pathname, myName)
      return tab ? `tab:${tab}` : location.key
    },
    [myName],
  )
  return (
    <AppShell
      current={current}
      links={{
        ...appLinks,
        search: searchUrl,
        profile: myName ? `/u/${encodeURIComponent(myName)}` : appLinks.profile,
      }}
      userName={myName}
      atRoot={tabRootOf(pathname, myName)}
    >
      <Outlet />
      <ScrollRestoration getKey={scrollKey} />
    </AppShell>
  )
}

/** Desktop: top bar with wordmark, sections and avatar. Mobile: wordmark on top, tabs at the bottom. */
function AppNav({
  links,
  current,
  userName = 'You',
  atRoot,
}: {
  links: NavLinks
  current?: Section
  userName?: string
  atRoot?: Section
}) {
  // On a tab's own page, its tab (or the wordmark for Feed, the avatar for Profile) scrolls back to
  // the top. Anywhere else it navigates, and the tab's page comes back at its own place.
  const toTopOn = (section: Section) => (event: MouseEvent) => {
    if (section !== atRoot) return
    event.preventDefault()
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reduce ? 'instant' : 'smooth' })
  }

  return (
    <>
      <header className="app-header sticky top-0 z-header border-b border-border bg-bg">
        <div className="mx-auto flex h-12 max-w-content items-center gap-8 px-4 lg:h-16 lg:px-6">
          <Link to={links.feed} onClick={toTopOn('feed')} className="flex h-tap items-center rounded-control font-display text-h3 font-bold text-text">
            crate
          </Link>
          <nav aria-label="Main" className="hidden lg:block">
            <ul className="flex gap-1">
              {desktopOrder.map((tab) => (
                <li key={tab.id}>
                  <Link
                    to={links[tab.id]}
                    onClick={toTopOn(tab.id)}
                    aria-current={tab.id === current ? 'page' : undefined}
                    className={`flex h-tap items-center rounded-control px-3 text-body active:bg-pressed ${
                      tab.id === current ? 'bg-surface-raised font-medium text-text' : 'text-muted'
                    }`}
                  >
                    {tab.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <Link to={links.profile} onClick={toTopOn('profile')} aria-label="Your profile" className="ml-auto hidden rounded-full lg:block">
            <Avatar name={userName.toUpperCase()} />
          </Link>
        </div>
      </header>

      <nav aria-label="Main" className="tabbar fixed inset-x-0 bottom-0 z-tabbar border-t border-border bg-bg lg:hidden">
        <ul className="grid h-tabbar grid-cols-3">
          {mobileOrder.map((tab) => {
            const active = tab.id === current
            const TabIcon = tab.icon
            return (
              <li key={tab.id}>
                <Link
                  to={links[tab.id]}
                  onClick={toTopOn(tab.id)}
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
