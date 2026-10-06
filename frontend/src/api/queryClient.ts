import { QueryClient } from '@tanstack/react-query'
import { clearSession } from '../auth/session.ts'
import { ApiError, setUnauthorizedHandler } from './client.ts'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 4xx won't fix itself on retry; a 5xx or a dropped connection might, once.
      retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 1,
      // Search can reach MusicBrainz; don't repeat it just because the tab got focus.
      refetchOnWindowFocus: false,
    },
  },
})

/** Forget the token and everything fetched with it. The route guard then shows Login. */
export function logout() {
  clearSession()
  queryClient.clear()
}

setUnauthorizedHandler(logout)
