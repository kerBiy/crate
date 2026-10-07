import { useMutation, useQuery } from '@tanstack/react-query'
import { setSession } from '../auth/session.ts'
import { api } from './client.ts'
import type { AlbumDetails, Me, SearchResponse, Token } from './types.ts'

export const searchLimit = 24
/** Shorter queries match too much to be useful, and each new query can cost several MusicBrainz calls. */
export const minSearchLength = 2

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => api<Me>('/users/me'), staleTime: Infinity })
}

export function useAlbumSearch(query: string) {
  const q = query.trim()
  return useQuery({
    queryKey: ['albums', 'search', q],
    queryFn: ({ signal }) =>
      api<SearchResponse>(`/albums/search?${new URLSearchParams({ q, limit: String(searchLimit) })}`, { signal }),
    enabled: q.length >= minSearchLength,
    staleTime: 5 * 60_000,
  })
}

export function useAlbum(id: string) {
  return useQuery({
    queryKey: ['albums', id],
    queryFn: ({ signal }) => api<AlbumDetails>(`/albums/${encodeURIComponent(id)}`, { signal }),
    staleTime: 5 * 60_000,
  })
}

type Credentials = { login: string; password: string }

async function login(credentials: Credentials) {
  const token = await api<Token>('/auth/login', { method: 'POST', body: JSON.stringify(credentials) })
  setSession(token)
}

export function useLogin() {
  return useMutation({ mutationFn: login })
}

export type Registration = { username: string; email: string; password: string; inviteCode: string }

/** Register returns no token, so sign in right after with the same credentials. */
export function useRegister() {
  return useMutation({
    mutationFn: async (registration: Registration) => {
      await api('/auth/register', { method: 'POST', body: JSON.stringify(registration) })
      await login({ login: registration.username, password: registration.password })
    },
  })
}
