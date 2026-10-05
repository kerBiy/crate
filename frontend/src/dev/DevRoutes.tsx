import { Navigate, Route, Routes } from 'react-router'
import { ComponentsPage } from './ComponentsPage.tsx'

/** Developer tools. Development only. */
export function DevRoutes() {
  return (
    <Routes>
      <Route index element={<Navigate to="components" replace />} />
      <Route path="components" element={<ComponentsPage />} />
    </Routes>
  )
}
