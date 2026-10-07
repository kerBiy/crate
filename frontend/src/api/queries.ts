import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { setSession } from '../auth/session.ts'
import { api, ApiError } from './client.ts'
import type { AlbumDetails, Me, Page, Review, ReviewWithAuthor, SearchResponse, Token, UserSummary } from './types.ts'

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

/** Stars on screen (0.5–5) ↔ what the API stores (1–10). */
export const toStars = (rating: number) => rating / 2
export const toRating = (stars: number) => Math.round(stars * 2)

const reviewKeys = {
  mine: (albumId: string) => ['reviews', albumId, 'mine'] as const,
  list: (albumId: string) => ['reviews', albumId, 'list'] as const,
}

/** My review of the album, or null when I haven't rated it (the API answers 404). */
export function useMyReview(albumId: string) {
  return useQuery({
    queryKey: reviewKeys.mine(albumId),
    queryFn: async ({ signal }) => {
      try {
        return await api<Review>(`/reviews/albums/${encodeURIComponent(albumId)}/me`, { signal })
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }
    },
  })
}

export const reviewsPageSize = 20

/**
 * The album's reviews, newest first, a page at a time (cursor pagination).
 * Each page brings its authors' names along (one batch call), so a page and its names load,
 * and fail, together.
 */
export function useAlbumReviews(albumId: string) {
  return useInfiniteQuery({
    queryKey: reviewKeys.list(albumId),
    initialPageParam: null as string | null,
    getNextPageParam: (last: Page<ReviewWithAuthor>) => last.nextCursor,
    queryFn: async ({ pageParam, signal }): Promise<Page<ReviewWithAuthor>> => {
      const params = new URLSearchParams({ limit: String(reviewsPageSize) })
      if (pageParam) params.set('cursor', pageParam)
      const page = await api<Page<Review>>(`/reviews/albums/${encodeURIComponent(albumId)}?${params}`, { signal })
      const ids = [...new Set(page.items.map((review) => review.userId))]
      const users = ids.length
        ? await api<{ items: UserSummary[] }>(`/users?${new URLSearchParams({ ids: ids.join(',') })}`, { signal })
        : { items: [] }
      const byId = new Map(users.items.map((user) => [user.id, user]))
      return {
        items: page.items.map((review) => ({ ...review, author: byId.get(review.userId) ?? null })),
        nextCursor: page.nextCursor,
      }
    },
  })
}

type ReviewInput = { rating: number; body: string | null }

/**
 * Rate or replace my review. The new value shows at once (optimistic) and rolls back on error.
 * Changes to one album's review run one after another (scope), so answers can't arrive out of order.
 */
export function useSaveReview(albumId: string) {
  const queryClient = useQueryClient()
  const key = reviewKeys.mine(albumId)
  return useMutation({
    scope: { id: `review-${albumId}` },
    mutationFn: (input: ReviewInput) =>
      api<Review>(`/reviews/albums/${encodeURIComponent(albumId)}`, { method: 'PUT', body: JSON.stringify(input) }),
    onMutate: async (input) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Review | null>(key)
      const now = new Date().toISOString()
      const optimistic: Review = {
        id: '',
        userId: '',
        albumId,
        createdAt: now,
        ...previous,
        ...input,
        updatedAt: now,
      }
      queryClient.setQueryData(key, optimistic)
      return { previous }
    },
    onError: (_error, _input, context) => queryClient.setQueryData(key, context?.previous ?? null),
    onSuccess: (review) => queryClient.setQueryData(key, review),
    onSettled: () => queryClient.invalidateQueries({ queryKey: reviewKeys.list(albumId) }),
  })
}

/** Remove my rating (and its text). Optimistic, like saving. */
export function useDeleteReview(albumId: string) {
  const queryClient = useQueryClient()
  const key = reviewKeys.mine(albumId)
  return useMutation({
    scope: { id: `review-${albumId}` },
    mutationFn: () => api<void>(`/reviews/albums/${encodeURIComponent(albumId)}`, { method: 'DELETE' }),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Review | null>(key)
      queryClient.setQueryData(key, null)
      return { previous }
    },
    onError: (_error, _input, context) => queryClient.setQueryData(key, context?.previous ?? null),
    onSettled: () => queryClient.invalidateQueries({ queryKey: reviewKeys.list(albumId) }),
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
