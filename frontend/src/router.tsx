import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { RedirectIfSignedIn, RequireAuth } from './auth/RequireAuth.tsx'
import { AppShellLayout } from './components/ui/AppShell.tsx'
import { AlbumPage } from './pages/AlbumPage.tsx'
import { LoginPage, RegisterPage } from './pages/AuthPages.tsx'
import { FeedPage } from './pages/FeedPage.tsx'
import { FollowListPage } from './pages/FollowListPage.tsx'
import { NotFoundPage } from './pages/PlaceholderPages.tsx'
import { MyProfileRedirect, ProfilePage } from './pages/ProfilePage.tsx'
import { SearchPage } from './pages/SearchPage.tsx'
import { SettingsPage } from './pages/SettingsPage.tsx'

// Design lab and component catalog, development only. Vite turns import.meta.env.DEV into
// `false` for the production build, so this branch and its dynamic imports are dropped and
// none of that code ends up in dist/. HydrateFallback renders nothing while the chunk loads.
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: '/lab/*',
        lazy: () => import('./lab/LabRoutes.tsx').then((m) => ({ Component: m.LabRoutes })),
        HydrateFallback: () => null,
      },
      {
        path: '/dev/*',
        lazy: () => import('./dev/DevRoutes.tsx').then((m) => ({ Component: m.DevRoutes })),
        HydrateFallback: () => null,
      },
    ]
  : []

export const router = createBrowserRouter([
  ...devRoutes,
  {
    element: <RedirectIfSignedIn />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
    ],
  },
  {
    // Everything else needs a session; without one, RequireAuth sends the user to Login.
    element: <RequireAuth />,
    children: [
      {
        element: <AppShellLayout />,
        children: [
          { index: true, element: <Navigate to="/feed" replace /> },
          { path: 'feed', element: <FeedPage /> },
          { path: 'search', element: <SearchPage /> },
          { path: 'albums/:id', element: <AlbumPage /> },
          { path: 'profile', element: <MyProfileRedirect /> },
          { path: 'u/:username', element: <ProfilePage /> },
          { path: 'u/:username/followers', element: <FollowListPage kind="followers" /> },
          { path: 'u/:username/following', element: <FollowListPage kind="following" /> },
          { path: 'settings', element: <SettingsPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
