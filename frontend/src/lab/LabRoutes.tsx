import { Navigate, Route, Routes } from 'react-router'
import { LabAlbumPage } from './LabAlbumPage.tsx'
import { LabGridPage } from './LabGridPage.tsx'
import { LabLayout } from './LabLayout.tsx'
import { LabPlaceholderPage } from './LabPlaceholderPage.tsx'

/** Design lab: static album page and cover grid, no backend. Development only. */
export function LabRoutes() {
  return (
    <Routes>
      <Route element={<LabLayout />}>
        <Route index element={<Navigate to="album/b1392450-e666-3926-a536-22c65f834433" replace />} />
        <Route path="grid" element={<LabGridPage />} />
        <Route path="feed" element={<LabPlaceholderPage page="feed" />} />
        <Route path="profile" element={<LabPlaceholderPage page="profile" />} />
        <Route path="album/:mbid" element={<LabAlbumPage />} />
      </Route>
    </Routes>
  )
}
