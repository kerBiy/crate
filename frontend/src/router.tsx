import { createBrowserRouter, Navigate } from 'react-router'
import { LabAlbumPage } from './lab/LabAlbumPage.tsx'
import { LabGridPage } from './lab/LabGridPage.tsx'
import { LabLayout } from './lab/LabLayout.tsx'
import { LabPlaceholderPage } from './lab/LabPlaceholderPage.tsx'
import { HomePage } from './pages/HomePage.tsx'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  {
    // Design lab: static album page and cover grid, no backend.
    path: '/lab',
    element: <LabLayout />,
    children: [
      { index: true, element: <Navigate to="/lab/album/b1392450-e666-3926-a536-22c65f834433" replace /> },
      { path: 'grid', element: <LabGridPage /> },
      { path: 'feed', element: <LabPlaceholderPage page="feed" /> },
      { path: 'profile', element: <LabPlaceholderPage page="profile" /> },
      { path: 'album/:mbid', element: <LabAlbumPage /> },
    ],
  },
])
