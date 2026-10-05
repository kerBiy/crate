import { House, MagnifyingGlass, User, type Icon } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { Avatar } from './album/bits.tsx'

export type Section = 'search' | 'feed' | 'profile'

type Tab = { id: Section; label: string; to: string; icon: Icon }

const desktopOrder: Tab[] = [
  { id: 'search', label: 'Search', to: '/lab/grid', icon: MagnifyingGlass },
  { id: 'feed', label: 'Feed', to: '/lab/feed', icon: House },
  { id: 'profile', label: 'Profile', to: '/lab/profile', icon: User },
]

// DESIGN.md section 8: mobile tabs are Feed, Search, Profile.
const mobileOrder = [desktopOrder[1], desktopOrder[0], desktopOrder[2]]

/** Desktop: top bar with wordmark, sections and avatar. Mobile: wordmark on top, tabs at the bottom. */
export function AppNav({ current }: { current: Section }) {
  return (
    <>
      <header className="sticky top-0 z-10 border-b border-border bg-bg">
        <div className="mx-auto flex h-12 max-w-content items-center gap-8 px-4 lg:h-16 lg:px-6">
          <Link to="/lab/grid" className="font-display text-h3 font-bold text-text">
            crate
          </Link>
          <nav aria-label="Main" className="hidden lg:block">
            <ul className="flex gap-1">
              {desktopOrder.map((tab) => (
                <li key={tab.id}>
                  <Link
                    to={tab.to}
                    aria-current={tab.id === current ? 'page' : undefined}
                    className={`flex h-tap items-center rounded-control px-3 text-body ${
                      tab.id === current ? 'bg-surface font-medium text-text' : 'text-muted'
                    }`}
                  >
                    {tab.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <Link to="/lab/profile" aria-label="Your profile" className="ml-auto hidden rounded-full lg:block">
            <Avatar name="You" />
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
                  to={tab.to}
                  aria-current={active ? 'page' : undefined}
                  className={`flex h-full flex-col items-center justify-center gap-1 text-meta ${
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
