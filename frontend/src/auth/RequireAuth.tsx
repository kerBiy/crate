import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from './session.ts'

export type FromState = { from?: string } | null

/** Signed-in pages. Without a session, go to Login and come back here afterwards. */
export function RequireAuth() {
  const session = useSession()
  const location = useLocation()
  if (!session) {
    const from = location.pathname + location.search
    return <Navigate to="/login" replace state={{ from } satisfies FromState} />
  }
  return <Outlet />
}

/** Login and Register. Already signed in: nothing to do here. */
export function RedirectIfSignedIn() {
  const session = useSession()
  const location = useLocation()
  if (session) return <Navigate to={(location.state as FromState)?.from ?? '/feed'} replace />
  return <Outlet />
}
