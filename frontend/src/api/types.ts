// Response shapes, mirroring the services' records.

/** user-service MeResponse. */
export type Me = {
  id: string
  username: string
  email: string
  displayName: string | null
  createdAt: string
}

/** user-service TokenResponse. */
export type Token = { accessToken: string; expiresAt: string }

/** catalog-service AlbumSummary: one album in a list. */
export type AlbumSummary = {
  id: string
  title: string
  artistCredit: string
  year: string | null
  coverUrl: string
  avgRating: number | null
  ratingCount: number
}

/** catalog-service AlbumDetails: the album page. */
export type AlbumDetails = AlbumSummary & {
  primaryArtistId: string | null
  primaryType: string | null
  /** MusicBrainz dates can be partial: "1997", "1997-05" or "1997-05-21". */
  firstReleaseDate: string | null
  /** Ratings per half-star value: 10 entries, ½ star first, 5 stars last. */
  ratingDistribution: number[]
}

/** catalog-service SearchResponse. partial: MusicBrainz couldn't be asked, results may be missing. */
export type SearchResponse = { items: AlbumSummary[]; partial: boolean }

/** review-service ReviewResponse. rating is in half stars: 1 = ½ star, 10 = 5 stars. */
export type Review = {
  id: string
  userId: string
  albumId: string
  rating: number
  body: string | null
  createdAt: string
  updatedAt: string
}

/** One page of a cursor-paginated list. nextCursor is null on the last page. */
export type Page<T> = { items: T[]; nextCursor: string | null }

/** user-service UserSummary: someone next to their activity. */
export type UserSummary = { id: string; username: string; displayName: string | null }

/** A review with its author, ready to show. author is null when the account no longer exists. */
export type ReviewWithAuthor = Review & { author: UserSummary | null }
