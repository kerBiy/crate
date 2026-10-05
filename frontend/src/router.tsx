import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { AppShellLayout } from './components/ui/AppShell.tsx'
import { FeedPage, NotFoundPage, ProfilePage, SearchPage } from './pages/PlaceholderPages.tsx'

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
    element: <AppShellLayout />,
    children: [
      { index: true, element: <Navigate to="/feed" replace /> },
      { path: 'feed', element: <FeedPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
