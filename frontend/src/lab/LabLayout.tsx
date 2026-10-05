import { Outlet, useLocation } from 'react-router'
import { AppNav, type Section } from '../components/AppNav.tsx'
import { DevPanel } from './DevPanel.tsx'

/** Design lab: the real app navigation around the pages, lab controls tucked in a corner. */
export function LabLayout() {
  const { pathname } = useLocation()
  const section: Section = pathname.startsWith('/lab/feed')
    ? 'feed'
    : pathname.startsWith('/lab/profile')
      ? 'profile'
      : 'search'

  return (
    <div className="min-h-screen overflow-x-clip pb-tabbar lg:pb-0">
      <AppNav current={section} />
      <Outlet />
      <DevPanel />
    </div>
  )
}
