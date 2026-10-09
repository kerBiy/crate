import { useEffect, useEffectEvent, useRef } from 'react'
import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { setSession } from '../auth/session.ts'
import { api, ApiError } from './client.ts'
import type {
  AlbumDetails,
  AlbumSummary,
  FeedItem,
  Me,
  Page,
  Profile,
  RatedAlbum,
  Review,
  ReviewWithAuthor,
  SearchResponse,
  Token,
  UserSummary,
} from './types.ts'

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
    // A new query keeps the last results on screen (dimmed) instead of flashing the skeleton grid.
    placeholderData: keepPreviousData,
  })
}

const albumKey = (id: string) => ['albums', id] as const
const fetchAlbum = (id: string, signal?: AbortSignal) => api<AlbumDetails>(`/albums/${encodeURIComponent(id)}`, { signal })

export function useAlbum(id: string) {
  return useQuery({
    queryKey: albumKey(id),
    queryFn: ({ signal }) => fetchAlbum(id, signal),
    staleTime: 5 * 60_000,
  })
}

/**
 * The album's stats with one rating changed: added (old null), replaced, or removed (new null).
 * Same delta math as catalog-service's consumer. Ratings are 1–10 (half stars).
 */
export function applyRatingChange(album: AlbumDetails, oldRating: number | null, newRating: number | null): AlbumDetails {
  const distribution = [...album.ratingDistribution]
  if (oldRating !== null) distribution[oldRating - 1] -= 1
  if (newRating !== null) distribution[newRating - 1] += 1
  const ratingCount = distribution.reduce((total, count) => total + count, 0)
  const sum = distribution.reduce((total, count, i) => total + count * (i + 1), 0)
  // Rounded like the server: stars, one decimal.
  const avgRating = ratingCount ? Math.round((sum / ratingCount / 2) * 10) / 10 : null
  return { ...album, ratingDistribution: distribution, ratingCount, avgRating }
}

/** Refetch delays after a rating change, in ms: about 7.5 s in all. */
const reconcileDelays = [500, 1000, 2000, 4000]
const reconciling = new Map<string, AbortController>()

/**
 * Album stats are built by catalog-service from Kafka events, so right after a save they are a moment
 * behind (eventual consistency). The page shows the expected numbers at once, then asks the server
 * again until its stats move past `before` (the event was processed), and the server's numbers win.
 * If they never move (the event was lost, see ADR-006), the server's numbers are shown at the end anyway.
 */
function showRatingChange(queryClient: QueryClient, albumId: string, oldRating: number | null, newRating: number | null) {
  if (oldRating === newRating) return
  const before = queryClient.getQueryData<AlbumDetails>(albumKey(albumId))
  if (!before) return
  // A newer change to this album restarts the wait.
  reconciling.get(albumId)?.abort()
  const controller = new AbortController()
  reconciling.set(albumId, controller)
  // An in-flight fetch could land with the old numbers after our patch.
  void queryClient.cancelQueries({ queryKey: albumKey(albumId) })
  queryClient.setQueryData(albumKey(albumId), applyRatingChange(before, oldRating, newRating))

  const moved = (album: AlbumDetails) =>
    album.ratingCount !== before.ratingCount ||
    album.ratingDistribution.some((count, i) => count !== before.ratingDistribution[i])

  void (async () => {
    for (const [attempt, delay] of reconcileDelays.entries()) {
      await new Promise((resolve) => setTimeout(resolve, delay))
      if (controller.signal.aborted) return
      try {
        // Fetched outside the cache: a still-stale answer must not replace the expected numbers.
        const server = await fetchAlbum(albumId, controller.signal)
        if (moved(server) || attempt === reconcileDelays.length - 1) {
          queryClient.setQueryData(albumKey(albumId), server)
          break
        }
      } catch {
        // Offline or aborted: keep the expected numbers; the next visit refetches.
        if (controller.signal.aborted) return
      }
    }
    if (reconciling.get(albumId) === controller) reconciling.delete(albumId)
  })()
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
    onSuccess: (review, _input, context) => {
      queryClient.setQueryData(key, review)
      showRatingChange(queryClient, albumId, context?.previous?.rating ?? null, review.rating)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: reviewKeys.list(albumId) })
      void queryClient.invalidateQueries({ queryKey: socialKeys.ratingsAll })
    },
  })
}

/**
 * Remove my rating (and its text), with a window to undo it. The rating leaves the screen at once,
 * but the DELETE is only sent by commit(), when the window ends (the "Undo" toast closes).
 *
 * Why wait instead of deleting now and saving again on undo: a delete is final on the server. It
 * publishes ReviewDeleted, and saving again makes a new review (new id, new createdAt, a
 * ReviewCreated event), so an undone review would jump to the top of feeds as if just written, the
 * average would move twice, and a failed re-save would lose the text for good. Waiting means an undo
 * never reaches the server at all.
 *
 * The price: until commit, the server still has the rating (the average updates after). Leaving the
 * album page commits at once; closing the tab sends the DELETE as keepalive, so it outlives the page.
 */
export function useRemoveReview(albumId: string, onFailed: () => void) {
  const queryClient = useQueryClient()
  const key = reviewKeys.mine(albumId)
  const pending = useRef<Review | null>(null)
  const removal = useMutation({
    scope: { id: `review-${albumId}` },
    mutationFn: (review: Review) =>
      api<void>(`/reviews/albums/${encodeURIComponent(review.albumId)}`, { method: 'DELETE', keepalive: true }),
    onError: (_error, review) => {
      queryClient.setQueryData(key, review)
      onFailed()
    },
    onSuccess: (_data, review) => showRatingChange(queryClient, albumId, review.rating, null),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: reviewKeys.list(albumId) })
      void queryClient.invalidateQueries({ queryKey: socialKeys.ratingsAll })
    },
  })

  /** Take the rating off the screen; nothing is sent yet. */
  function remove() {
    const review = queryClient.getQueryData<Review | null>(key)
    if (!review) return
    void queryClient.cancelQueries({ queryKey: key })
    queryClient.setQueryData(key, null)
    pending.current = review
  }

  /** Put it back. The server never knew. */
  function undo() {
    if (!pending.current) return
    queryClient.setQueryData(key, pending.current)
    pending.current = null
  }

  /** The window is over: send the DELETE. Safe to call more than once. */
  function commit() {
    const review = pending.current
    if (!review) return
    pending.current = null
    removal.mutate(review)
  }

  /** A new rating replaces the pending removal: the server still has the row, so the save updates it. */
  function drop() {
    pending.current = null
  }

  const flush = useEffectEvent(commit)
  useEffect(() => {
    window.addEventListener('pagehide', flush)
    return () => {
      window.removeEventListener('pagehide', flush)
      flush()
    }
  }, [])

  return { remove, undo, commit, drop, removing: removal.isPending }
}

// ---------------------------------------------------------------------------
// People: profiles, follows, the feed.
// A list from review-service only holds ids. Each page asks for its people and albums in one batch
// call each (GET /users?ids=, GET /albums?ids=), never one request per row (the N+1 problem).
// ---------------------------------------------------------------------------

const socialKeys = {
  profile: (username: string) => ['profiles', username.toLowerCase()] as const,
  profilesAll: ['profiles'] as const,
  ratings: (userId: string) => ['ratings', userId] as const,
  ratingsAll: ['ratings'] as const,
  follows: (userId: string, kind: FollowListKind) => ['follows', userId, kind] as const,
  followsAll: ['follows'] as const,
  people: (q: string) => ['people', q] as const,
  feed: ['feed'] as const,
}

export const pageSize = 20

/** Users by id, in one call. Unknown ids are left out, so look them up by id. */
async function usersById(ids: string[], signal?: AbortSignal) {
  const unique = [...new Set(ids)]
  if (!unique.length) return new Map<string, UserSummary>()
  const { items } = await api<{ items: UserSummary[] }>(`/users?${new URLSearchParams({ ids: unique.join(',') })}`, { signal })
  return new Map(items.map((user) => [user.id, user]))
}

/** Albums by id, in one call. Only albums catalog has stored; others are left out. */
async function albumsById(ids: string[], signal?: AbortSignal) {
  const unique = [...new Set(ids)]
  if (!unique.length) return new Map<string, AlbumSummary>()
  const { items } = await api<{ items: AlbumSummary[] }>(`/albums?${new URLSearchParams({ ids: unique.join(',') })}`, { signal })
  return new Map(items.map((album) => [album.id, album]))
}

function pageParams(cursor: string | null) {
  const params = new URLSearchParams({ limit: String(pageSize) })
  if (cursor) params.set('cursor', cursor)
  return params
}

export function useProfile(username: string) {
  return useQuery({
    queryKey: socialKeys.profile(username),
    queryFn: ({ signal }) => api<Profile>(`/users/${encodeURIComponent(username)}`, { signal }),
  })
}

/** Someone's ratings, newest first, each with its album (one albums call per page). */
export function useUserRatings(userId: string | undefined) {
  return useInfiniteQuery({
    queryKey: socialKeys.ratings(userId ?? ''),
    enabled: !!userId,
    initialPageParam: null as string | null,
    getNextPageParam: (last: Page<RatedAlbum>) => last.nextCursor,
    queryFn: async ({ pageParam, signal }): Promise<Page<RatedAlbum>> => {
      const page = await api<Page<Review>>(`/reviews/users/${encodeURIComponent(userId!)}?${pageParams(pageParam)}`, { signal })
      const albums = await albumsById(page.items.map((review) => review.albumId), signal)
      return {
        items: page.items.map((review) => ({ ...review, album: albums.get(review.albumId) ?? null })),
        nextCursor: page.nextCursor,
      }
    },
  })
}

export type FollowListKind = 'followers' | 'following'

export function useFollowList(userId: string | undefined, kind: FollowListKind) {
  return useInfiniteQuery({
    queryKey: socialKeys.follows(userId ?? '', kind),
    enabled: !!userId,
    initialPageParam: null as string | null,
    getNextPageParam: (last: Page<UserSummary>) => last.nextCursor,
    queryFn: ({ pageParam, signal }) =>
      api<Page<UserSummary>>(`/users/${encodeURIComponent(userId!)}/${kind}?${pageParams(pageParam)}`, { signal }),
  })
}

export function usePeopleSearch(query: string) {
  const q = query.trim()
  return useQuery({
    queryKey: socialKeys.people(q),
    queryFn: ({ signal }) => api<{ items: UserSummary[] }>(`/users/search?${new URLSearchParams({ q })}`, { signal }),
    enabled: q.length >= minSearchLength,
    placeholderData: keepPreviousData,
  })
}

/**
 * Friends feed v1. review-service finds who I follow and their reviews; then names and albums for
 * the whole page arrive in two batch calls, sent together. 3 requests a page, however long it is.
 */
export function useFeed() {
  return useInfiniteQuery({
    queryKey: socialKeys.feed,
    initialPageParam: null as string | null,
    getNextPageParam: (last: Page<FeedItem>) => last.nextCursor,
    queryFn: async ({ pageParam, signal }): Promise<Page<FeedItem>> => {
      const page = await api<Page<Review>>(`/reviews/feed?${pageParams(pageParam)}`, { signal })
      const [users, albums] = await Promise.all([
        usersById(page.items.map((review) => review.userId), signal),
        albumsById(page.items.map((review) => review.albumId), signal),
      ])
      return {
        items: page.items.map((review) => ({
          ...review,
          author: users.get(review.userId) ?? null,
          album: albums.get(review.albumId) ?? null,
        })),
        nextCursor: page.nextCursor,
      }
    },
  })
}

/**
 * Follow or unfollow the profile's owner. The button and the follower count change at once and roll
 * back on error. One person's follow changes run in order (scope), so fast clicks can't cross.
 */
export function useFollow(profile: Profile) {
  const queryClient = useQueryClient()
  const key = socialKeys.profile(profile.username)
  return useMutation({
    scope: { id: `follow-${profile.id}` },
    mutationFn: (follow: boolean) =>
      api<void>(`/users/${encodeURIComponent(profile.id)}/follow`, { method: follow ? 'PUT' : 'DELETE' }),
    onMutate: async (follow) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Profile>(key)
      if (previous && previous.followedByMe !== follow) {
        queryClient.setQueryData<Profile>(key, {
          ...previous,
          followedByMe: follow,
          followerCount: previous.followerCount + (follow ? 1 : -1),
        })
      }
      return { previous }
    },
    onError: (_error, _follow, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
    },
    onSettled: () => {
      // My own following count, both lists and the feed all changed.
      void queryClient.invalidateQueries({ queryKey: socialKeys.profilesAll })
      void queryClient.invalidateQueries({ queryKey: socialKeys.followsAll })
      void queryClient.invalidateQueries({ queryKey: socialKeys.feed })
    },
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
